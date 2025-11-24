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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
/**
 * 所有构建器的抽象基类
 * 封装了构建过程中通用的配置引用、类型解析、数据转换、对象创建等工具方法
 * 为子类（如 XMLConfigBuilder、XMLMapperBuilder、MapperAnnotationBuilder 等）提供统一的基础能力
 * 该类遵循模板方法模式，将通用的构建逻辑抽离到父类，子类专注于实现特定的解析和构建逻辑。
 */
public abstract class BaseBuilder {
  /**
   * 所有属性均为 protected final，保证子类可访问且初始化后不可修改，是 MyBatis 构建过程的核心依赖。
   * MyBatis 的全局配置核心对象，包含了 MyBatis 运行所需的所有配置（环境、映射器、类型别名、类型处理
   * **** 子类的所有构建逻辑最终都会将解析后的配置存入该对象，或从该对象中获取已有的配置。****
   */
  protected final Configuration configuration;
  /**
   * 类型别名注册中心，由 configuration 提供（configuration.getTypeAliasRegistry()）。
   * 解析配置中的类型别名（如将 int 解析为 java.lang.Integer，将 User 解析为 com.example.entity.User），简化配置文件的编写。
   */
  protected final TypeAliasRegistry typeAliasRegistry;
  /**
   * 类型处理器注册中心，由 configuration 提供（configuration.getTypeHandlerRegistry()）。
   * 管理 Java 类型与 JDBC 类型之间的转换处理器（TypeHandler），在构建 SQL 映射时，解析并绑定对应的类型处理器。
   */
  protected final TypeHandlerRegistry typeHandlerRegistry;

  public BaseBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
    this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  /**
   * 正则表达式解析方法
   * @param regex
   * @param defaultValue
   * @return
   */
  protected Pattern parseExpression(String regex, String defaultValue) {
    return Pattern.compile(regex == null ? defaultValue : regex);
  }

  /**
   * 通用工具方法
   * 将字符串转为boolean值，值为null返回默认值
   * @param value
   * @param defaultValue
   * @return
   */
  protected Boolean booleanValueOf(String value, Boolean defaultValue) {
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  /**
   * 字符串转为整形，默认值
   * @param value
   * @param defaultValue
   * @return
   */
  protected Integer integerValueOf(String value, Integer defaultValue) {
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  /**
   * 将逗号分割的字符串转为HashSet，为null时使用默认值
   * @param value
   * @param defaultValue
   * @return
   */
  protected Set<String> stringSetValueOf(String value, String defaultValue) {
    value = (value == null ? defaultValue : value);
    return new HashSet<String>(Arrays.asList(value.split(",")));
  }

  /**
   * 将字符串“VARCHAR”转为对应的JDBCType
   * @param alias
   * @return
   */
  protected JdbcType resolveJdbcType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return JdbcType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
    }
  }

  /**
   * 将String转为ResultType类型
   * @param alias
   * @return
   */
  protected ResultSetType resolveResultSetType(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ResultSetType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
    }
  }

  /**
   * 同理
   * @param alias
   * @return
   */
  protected ParameterMode resolveParameterMode(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return ParameterMode.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
    }
  }


  protected Object createInstance(String alias) {
    Class<?> clazz = resolveClass(alias);
    if (clazz == null) {
      return null;
    }
    try {
      return resolveClass(alias).newInstance();
    } catch (Exception e) {
      throw new BuilderException("Error creating instance. Cause: " + e, e);
    }
  }

  /**
   * 封装 resolveAlias 的异常，将解析失败的异常转换为 BuilderException，统一构建过程的异常类型。
   * @param alias
   * @return
   */
  protected Class<?> resolveClass(String alias) {
    if (alias == null) {
      return null;
    }
    try {
      return resolveAlias(alias);
    } catch (Exception e) {
      throw new BuilderException("Error resolving class. Cause: " + e, e);
    }
  }

  /**
   * 解析类型处理器
   * @param javaType
   * @param typeHandlerAlias
   * @return
   */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
    if (typeHandlerAlias == null) {
      return null;
    }
    Class<?> type = resolveClass(typeHandlerAlias);
    //如果不是TypeHandler的子类,报错
    if (type != null && !TypeHandler.class.isAssignableFrom(type)) {
      throw new BuilderException("Type " + type.getName() + " is not a valid TypeHandler because it does not implement TypeHandler interface");
    }
    @SuppressWarnings( "unchecked" ) // already verified it is a TypeHandler
    Class<? extends TypeHandler<?>> typeHandlerType = (Class<? extends TypeHandler<?>>) type;
    //再去调用另一个重载的方法
    return resolveTypeHandler(javaType, typeHandlerType);
  }

  /**
   * 从注册中心获取或者创建类型处理器
   *
   * @param javaType Java类型
   * @param typeHandlerType 类型处理器类型
   * @return 解决的类型处理器，如果无法解决则返回null
   */
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
    if (typeHandlerType == null) {
      return null;
    }
    TypeHandler<?> handler = typeHandlerRegistry.getMappingTypeHandler(typeHandlerType);
    if (handler == null) {
      handler = typeHandlerRegistry.getInstance(javaType, typeHandlerType);
    }
    return handler;
  }

  /**
   * MyBatis 中类型别名机制的核心实现
   * 用于将配置中的别名解析为实际的 Class 对象，并创建实例。
   * @param alias
   * @return
   */
  protected Class<?> resolveAlias(String alias) {
    return typeAliasRegistry.resolveAlias(alias);
  }
}
