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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
/**
 * SQL源码构建器
 * SqlSourceBuilder 是 MyBatis 构建静态 SQL 源（StaticSqlSource）的核心类，
 * 它会将包含 #{} 占位符的原始 SQL 字符串（如 SELECT * FROM user WHERE id = #{id,jdbcType=INTEGER}）解析为带 ? 占位符的预编译 SQL
 * （如 SELECT * FROM user WHERE id = ?），同时构建 ParameterMapping 列表（记录每个 ? 对应的参数配置），是 MyBatis 实现参数绑定的关键前置步骤。
 */
public class SqlSourceBuilder extends BaseBuilder {

  /**
   * 映射支持的合法属性
   * 作用：限制 #{} 中可配置的属性，比如不允许写 #{id,xxx=123}（xxx 不在列表中），否则抛出异常；
   * 包含的合法属性：
   * javaType：参数的 Java 类型（如 java.lang.Integer）；
   * jdbcType：参数的 JDBC 类型（如 INTEGER）；
   * mode：参数模式（IN/OUT/INOUT，仅存储过程用）；
   * numericScale：数值精度（仅小数类型用）；
   * resultMap：结果映射（极少用）；
   * typeHandler：自定义类型处理器；
   * jdbcTypeName：JDBC 类型名称（兼容特殊数据库）。
   */
  private static final String parameterProperties = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 核心流程拆解：
   * 令牌处理器初始化：ParameterMappingTokenHandler 负责处理每个 #{} 内的参数表达式，构建 ParameterMapping；
   * 令牌解析器初始化：GenericTokenParser 是 MyBatis 通用的令牌解析工具，规则：
   * 开始标记：#{；
   * 结束标记：}；
   * 处理器：ParameterMappingTokenHandler（处理匹配到的令牌内容）；
   * SQL 解析与替换：parser.parse(originalSql) 会遍历原始 SQL，将所有 #{...} 替换为 ?，同时调用 handler.handleToken 处理每个 #{} 内的内容；
   * 构建静态 SQL 源：StaticSqlSource 是 MyBatis 最简单的 SqlSource，包含 “预编译 SQL + 参数映射列表”，后续直接用于创建 PreparedStatement。
   *
   * 以实际 SQL 解析为例，直观展示 SqlSourceBuilder 的执行效果：
   * 输入
   * 原始 SQL：SELECT * FROM user WHERE id = #{id,jdbcType=INTEGER} AND name = #{name,jdbcType=VARCHAR,typeHandler=MyTypeHandler}；
   * 参数类型：User.class；
   * 附加参数：null。
   * 解析过程
   * GenericTokenParser 匹配第一个 #{id,jdbcType=INTEGER}：
   * 调用 handleToken("id,jdbcType=INTEGER")；
   * ParameterExpression 解析为 {property=id, jdbcType=INTEGER}；
   * 构建 ParameterMapping（property=id，jdbcType=INTEGER，javaType=Integer）；
   * 返回 ?，替换该占位符；
   * 匹配第二个 #{name,jdbcType=VARCHAR,typeHandler=MyTypeHandler}：
   * 解析为 {property=name, jdbcType=VARCHAR, typeHandler=MyTypeHandler}；
   * 构建 ParameterMapping（property=name，jdbcType=VARCHAR，javaType=String，typeHandler=MyTypeHandler）；
   * 返回 ?，替换该占位符；
   * 最终生成：
   * 预编译 SQL：SELECT * FROM user WHERE id = ? AND name = ?；
   * ParameterMapping 列表：包含 2 个 ParameterMapping，分别对应 id 和 name 的配置；
   * 返回 StaticSqlSource，包含上述 SQL 和参数映射列表。
   * @param originalSql
   * @param parameterType
   * @param additionalParameters
   * @return
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    // 创建参数映射令牌处理器（核心处理器，处理#{...}内容）
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // 创建通用令牌解析器，匹配#{和}之间的内容
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    // 解析原始SQL，将#{...}替换为?，同时触发handler构建ParameterMapping
    String sql = parser.parse(originalSql);
    // 创建StaticSqlSource（静态SQL源），包含预编译SQL和参数映射列表
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  /**
   * ParameterMappingTokenHandler 是 SqlSourceBuilder 的核心内部类，
   * 实现 TokenHandler 接口，负责处理每个 #{} 令牌，构建 ParameterMapping。
   */
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    /**
     * 存储解析后的ParameterMapping列表（每个#{...}对应一个）
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
    /**
     * 参数类型，如User。class，Map.class
     */
    private Class<?> parameterType;
    /**
     * 附加参数的元对象，用于获取附加参数的类型，如分页参数
     */
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      // 为附加参数创建MetaObject，便于获取参数类型
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      // 步骤1：解析#{...}内的内容（如id,jdbcType=INTEGER），构建ParameterMapping并加入列表
      parameterMappings.add(buildParameterMapping(content));
      // 步骤2：返回?，替换原始SQL中的#{...}
      return "?";
    }

    //构建参数映射
    private ParameterMapping buildParameterMapping(String content) {
      Map<String, String> propertiesMap = parseParameterMapping(content);、
      // 获得属性名
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      // 优先级1：从附加参数中获取类型（如分页参数）
      if (metaParameters.hasGetter(property)) {
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        // 优先级2：如果参数类型本身有类型处理器（如基本类型），直接用参数类型
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        // 优先级3：如果jdbcType是CURSOR（游标），类型为ResultSet
        propertyType = java.sql.ResultSet.class;
      } else if (property != null) {
        // 优先级4：从参数类型的MetaClass中获取属性类型（如User.id → Integer）
        MetaClass metaClass = MetaClass.forClass(parameterType);
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      } else {
        // 默认Object
        propertyType = Object.class;
      }
      // 初始化 ParameterMapping 构建器
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;

      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + parameterProperties);
        }
      }
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        // 调用之前讲的ParameterExpression解析参数表达式（如id,jdbcType=INTEGER → {property=id,jdbcType=INTEGER}）
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }
  
}
