/*
 *    Copyright 2009-2013 the original author or authors.
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

import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;

/**
 * 是 MyBatis 中功能最灵活的主键生成器（实现KeyGenerator接口），
 * 核心作用是通过执行自定义 SQL 语句（而非依赖数据库自增 / 序列）生成主键，
 * 并且支持 “插入前” 或 “插入后” 执行该 SQL，是处理复杂主键生成场景（如自定义查询生成主键、兼容特殊数据库主键规则）的核心实现类。
 *
 *
 * 自定义 SQL 生成主键：通过预定义的keyStatement（包含自定义 SQL，如查询序列、UUID、雪花 ID 等）生成主键值；
 * 灵活控制执行时机：通过executeBefore参数控制自定义 SQL 在INSERT前 / 后执行（适配不同主键生成规则）；
 * 支持单 / 多主键回填：既能处理单主键，也能处理复合主键（多字段主键）的赋值；
 * 严格的校验逻辑：保证主键查询结果唯一、属性赋值有对应的 setter 方法，避免赋值失败。
 */
public class SelectKeyGenerator implements KeyGenerator {

  /**
   * SelectKey对应的MappedStatement后缀（用于区分主INSERT语句和主键查询语句）
   */
  public static final String SELECT_KEY_SUFFIX = "!selectKey";
  /**
   * 空值自定义SQL的执行实际，true表示INSERT前执行
   */
  private boolean executeBefore;
  /**
   * 封装了主键查询SQL的MappedStatement（如查询序列的SQL、生成UUID的SQL）
   */
  private MappedStatement keyStatement;

  public SelectKeyGenerator(MappedStatement keyStatement, boolean executeBefore) {
    this.executeBefore = executeBefore;
    this.keyStatement = keyStatement;
  }

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    if (executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    if (!executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  /**
   * processGeneratedKeys是SelectKeyGenerator中真正执行主键生成与回填的核心方法，
   * 它的核心逻辑是：执行<selectKey>标签中定义的自定义 SQL，校验查询结果的合法性，
   * 然后将唯一的查询结果转换后回填到插入参数对象的指定主键属性中。
   * 这个方法是SelectKeyGenerator实现 “自定义 SQL 生成主键” 的核心落地逻辑，我会逐行拆解并解释每个步骤的设计意图和细节。
   * @param executor
   * @param ms
   * @param parameter
   */
  private void processGeneratedKeys(Executor executor, MappedStatement ms, Object parameter) {
    try {
      // 校验三要素：参数非空、主键查询语句非空、主键属性非空
      if (parameter != null && keyStatement != null && keyStatement.getKeyProperties() != null) {
        // 主键属性名
        String[] keyProperties = keyStatement.getKeyProperties();
        final Configuration configuration = ms.getConfiguration();
        final MetaObject metaParam = configuration.newMetaObject(parameter);


        if (keyProperties != null) {
          // 创建专用的SIMPLE执行器（独立执行主键查询，不影响主执行器）
          Executor keyExecutor = configuration.newExecutor(executor.getTransaction(), ExecutorType.SIMPLE);

          /**
           *   // 步骤2：执行主键查询SQL，获取结果列表
           *   List<Object> values = keyExecutor.query(
           *     keyStatement,        // 包含<selectKey>中SQL的MappedStatement
           *     parameter,           // 复用INSERT的参数（支持SQL中引用参数，如#{name}）
           *     RowBounds.DEFAULT,   // 默认分页（不分页）
           *     Executor.NO_RESULT_HANDLER // 无自定义结果处理器，返回完整结果列表
           *   );
           */
          List<Object> values = keyExecutor.query(keyStatement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
          // 无结果
          if (values.size() == 0) {
            throw new ExecutorException("SelectKey returned no data.");
          } else if (values.size() > 1) {
            // 多结果
            throw new ExecutorException("SelectKey returned more than one value.");
          } else {
            MetaObject metaResult = configuration.newMetaObject(values.get(0));
            if (keyProperties.length == 1) {
              if (metaResult.hasGetter(keyProperties[0])) {
                setValue(metaParam, keyProperties[0], metaResult.getValue(keyProperties[0]));
              } else {
                setValue(metaParam, keyProperties[0], values.get(0));
              }
            } else {
              handleMultipleProperties(keyProperties, metaParam, metaResult);
            }
          }
        }
      }
    } catch (ExecutorException e) {
      throw e;
    } catch (Exception e) {
      throw new ExecutorException("Error selecting key or setting result to parameter object. Cause: " + e, e);
    }
  }

  private void handleMultipleProperties(String[] keyProperties,
      MetaObject metaParam, MetaObject metaResult) {
    String[] keyColumns = keyStatement.getKeyColumns();
      
    if (keyColumns == null || keyColumns.length == 0) {
      for (int i = 0; i < keyProperties.length; i++) {
        setValue(metaParam, keyProperties[i], metaResult.getValue(keyProperties[i]));
      }
    } else {
      if (keyColumns.length != keyProperties.length) {
        throw new ExecutorException("If SelectKey has key columns, the number must match the number of key properties.");
      }
      for (int i = 0; i < keyProperties.length; i++) {
        setValue(metaParam, keyProperties[i], metaResult.getValue(keyColumns[i]));
      }
    }
  }

  private void setValue(MetaObject metaParam, String property, Object value) {
    if (metaParam.hasSetter(property)) {
      metaParam.setValue(property, value);
    } else {
      throw new ExecutorException("No setter found for the keyProperty '" + property + "' in " + metaParam.getOriginalObject().getClass().getName() + ".");
    }
  }
}
