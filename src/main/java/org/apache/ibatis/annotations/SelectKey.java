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

import org.apache.ibatis.mapping.StatementType;

/**
 * 手动获取数据库生成主键
 * 它的核心价值在于解决了非自增主键（如 Oracle 序列、UUID 生成主键）的获取问题
 * **** 相当于在Java中设置非自增主键，并将其作为数据库主键执行插入操作 ****
 * 该注解仅作用于插入方法（@Insert/@InsertProvider标注的方法），
 * 通过执行指定的 SQL 语句（如查询 Oracle 序列、MySQL 自增主键）获取主键值，并将其赋值给实体类的指定属性，再执行真正的插入 SQL。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SelectKey {

  /**
   * 指定用于获取主键的SQL语句
   * （支持数组形式，实际仅执行第一条），如 Oracle 序列查询SELECT SEQ_USER.NEXTVAL FROM DUAL、MySQL 自增主键查询SELECT LAST_INSERT_ID()。
   * @return
   */
  String[] statement();

  /**
   * 指定实体类中存储主键的属性名
   * @return
   */
  String keyProperty();

  /**
   * 指定数据库表中的主键列名
   * @return
   */
  String keyColumn() default "";

  /**
   * 指定主键查询SQL的执行实际
   * - true：执行插入 SQL之前执行主键查询，将主键值赋值给实体后，再插入数据库（适用于 Oracle 序列、UUID 等预生成主键的场景）；
   * - false：执行插入 SQL之后执行主键查询，再将主键值赋值给实体（适用于 MySQL 自增主键等数据库自动生成主键的场景）。
   * @return
   */
  boolean before();

  /**
   * 主键的Java类型
   * @return
   */
  Class<?> resultType();

  /**
   * 指定主键查询的SQL的JDBC语句类型
   * - PREPARED（默认）：使用PreparedStatement，支持参数占位符；
   * - STATEMENT：使用Statement，仅支持静态 SQL；
   * - CALLABLE：使用CallableStatement，用于执行存储过程。
   * @return
   */
  StatementType statementType() default StatementType.PREPARED;
}
