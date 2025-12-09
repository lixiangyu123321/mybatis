/*
 *    Copyright 2009-2012 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;


/**
 * 是 MyBatis 中基于JDBC 3.0 规范实现的主键生成器（KeyGenerator），
 * 核心作用是在执行 INSERT 语句后，通过Statement.getGeneratedKeys()获取数据库自动生成的主键（如 MySQL 的自增 ID、Oracle 的序列），
 * 并将主键值回填到插入的参数对象中。
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  /**
   * Jdbc3KeyGenerator仅实现processAfter，因为自增主键只有插入后才能获取。
   *
   * 将单个插入参数包装为 List，适配processBatch的批量处理逻辑；
   * 调用processBatch执行主键获取和回填的核心逻辑；
   * 支持单条插入场景（日常开发最常用）。
   * @param executor
   * @param ms
   * @param stmt
   * @param parameter
   */
  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    List<Object> parameters = new ArrayList<Object>();
    parameters.add(parameter);
    processBatch(ms, stmt, parameters);
  }

  /**
   * 处理批量 / 单条插入主键回填的核心方法，
   * 它的核心逻辑是：从 JDBC 的Statement中获取数据库生成的主键结果集，然后遍历每个插入参数，
   * 将对应的主键值通过 TypeHandler 转换后回填到参数对象中。这个方法同时支持单条插入和批量插入场景
   *
   * 获取主键结果集：通过 JDBC 3.0 的stmt.getGeneratedKeys()获取数据库生成的主键（自增 ID 等）；
   * 校验匹配性：确保主键属性数量不超过结果集列数，避免越界；
   * 批量回填：遍历每个插入参数，将结果集中对应的主键值转换并设置到参数对象的指定属性中；
   * 资源释放：确保 ResultSet 最终关闭，避免资源泄漏；
   * 异常统一：将所有异常包装为 MyBatis 的ExecutorException，融入统一异常体系。
   * @param ms MyBatis的映射语句（包含Mapper配置：keyProperty、配置信息等）
   * @param stmt 执行INSERT的JDBC Statement
   * @param parameters 插入的参数列表，这里的参数指的是插入的数据对应的实体类
   */
  public void processBatch(MappedStatement ms, Statement stmt, List<Object> parameters) {
    ResultSet rs = null;
    try {
      // 获得数据库生成的主键ResultSet（核心JDBC API）
      rs = stmt.getGeneratedKeys();
      final Configuration configuration = ms.getConfiguration();
      final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      /**
       * 为什么有多个主键，考虑复合主键的情况
       */
      final String[] keyProperties = ms.getKeyProperties();
      /**
       *  结果集元数据（列数、列类型等）
       */
      final ResultSetMetaData rsmd = rs.getMetaData();
      TypeHandler<?>[] typeHandlers = null;
      // 校验主键属性于ResultSet列数匹配
      /**
       * （1）rs.next()：参数与主键行的一一对应
       * 单条插入：parameters有 1 个元素，rs.next()移到第 1 行（唯一的主键行）；
       * 批量插入：比如插入 3 个 User 对象，parameters有 3 个元素，rs.next()依次移到第 1、2、3 行，每行对应一个 User 的主键；
       * 若结果集行数 < 参数数量（比如插入 3 条但只返回 2 个主键），rs.next()返回 false，终止循环，避免空指针。
       * （2）MetaObject metaParam：统一属性操作
       * 无论参数是 JavaBean（User）、Map、还是其他类型，MetaObject都能通过统一的 API（setValue）设置属性，无需区分参数类型；
       * 比如参数是 Map 时，metaParam.setValue("id", 1001)等价于map.put("id", 1001)；参数是 User 时，等价于user.setId(1001)。
       * （3）延迟初始化typeHandlers
       * 类型处理器数组只需创建一次（所有参数的主键属性类型相同），延迟初始化 + 复用提升性能；
       * 比如批量插入 100 个 User 对象，只需调用 1 次getTypeHandlers，而非 100 次。
       * （4）populateKeys：最终回填操作
       * 调用你之前解析的populateKeys方法，将当前行的主键列值转换后设置到参数对象中。
       */
      if (keyProperties != null && rsmd.getColumnCount() >= keyProperties.length) {
        // 遍历每个插入参数，回填主键
        for (Object parameter : parameters) {
          if (!rs.next()) {
            // 无更多主键，终止循环
            break;
          }
          // 创建参数对象的MetaObject（MyBatis的属性操作工具）
          final MetaObject metaParam = configuration.newMetaObject(parameter);
          if (typeHandlers == null) {
            // 初始化类型处理器
            typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties);
          }
          // 将ResultSet中的主键值回填到参数对象中
          populateKeys(rs, metaParam, keyProperties, typeHandlers);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  /**
   * 获得主键类型处理器
   * 为每个主键属性（如id）匹配对应的 TypeHandler 类型处理器
   * TypeHandler 是 MyBatis 的类型转换接口，负责 Java 类型与 JDBC 类型的互转；
   * 例如：数据库返回的自增 ID 是 BIGINT，Java 实体的 id 是 Long，LongTypeHandler会将 ResultSet 中的值转为 Long 类型。
   * @param typeHandlerRegistry
   * @param metaParam
   * @param keyProperties
   * @return
   */
  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties) {
    /**
     * 校验是否有可赋值的 setter 方法；
     * 获取该属性的 Java 类型（如 Long、Integer）；
     * 从 MyBatis 的类型处理器注册表中找到适配该类型的 TypeHandler；
     * 将所有 TypeHandler 封装为数组返回，供后续主键回填使用。
     */
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    for (int i = 0; i < keyProperties.length; i++) {
      if (metaParam.hasSetter(keyProperties[i])) {
        Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
        TypeHandler<?> th = typeHandlerRegistry.getTypeHandler(keyPropertyType);
        typeHandlers[i] = th;
      }
    }
    return typeHandlers;
  }

  /**
   * 主键回填的最终核心方法—— 它的作用是将数据库生成的主键值（从ResultSet中读取），
   * 通过 TypeHandler 完成类型转换后，最终设置到插入的参数对象（如 User 实体）的指定属性（如id）中，
   * 实现 “插入后自动获取并回填主键” 的核心功能。
   * @param rs
   * @param metaParam
   * @param keyProperties
   * @param typeHandlers
   * @throws SQLException
   */
  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    for (int i = 0; i < keyProperties.length; i++) {
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
        Object value = th.getResult(rs, i + 1);
        metaParam.setValue(keyProperties[i], value);
      }
    }
  }

}
