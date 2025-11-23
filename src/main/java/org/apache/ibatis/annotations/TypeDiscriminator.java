/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * ****鉴别器映射器 ****
 * 替代了 XML 映射文件中的<discriminator>标签。其核心作用是根据数据库中某一列（鉴别列）的不同值，动态选择不同的结果映射规则
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TypeDiscriminator {
  /**
   * 指定鉴别列
   * MyBatis 会根据该列的值选择对应的@Case分支规则。
   * @return
   */
  String column();

  /**
   * 指定鉴别列值的Java 类型
   * @return
   */
  Class<?> javaType() default void.class;

  /**
   * 指定鉴别列的JDBC类型
   * @return
   */
  JdbcType jdbcType() default JdbcType.UNDEFINED;

  /**
   * 指定鉴别列的自定义类型处理器，用于转换鉴别列的数据库值与 Java 匹配值。
   * @return
   */
  Class<? extends TypeHandler<?>> typeHandler() default UnknownTypeHandler.class;

  /**
   * 分支规则，即根据鉴别列的值选择的结果映射规则
   * @return
   */
  Case[] cases();
}
