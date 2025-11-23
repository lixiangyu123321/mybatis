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

import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.StatementType;

/**
 * **** 用于对Mapper方法的精细化控制 ****
 * 是 MyBatis 中为 Mapper 接口方法配置 SQL 执行相关选项的核心注解
 * 覆盖缓存策略、结果集类型、语句类型、主键生成、超时时间等多维度执行规则。
 * 它替代了 XML 映射文件中<select>、<insert>、<update>、<delete>标签的同名属性，
 * 实现了注解式开发中对 SQL 执行细节的精细化控制，可标注在任意 Mapper 方法上（查询、插入、更新、删除），根据方法类型生效不同的属性。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Options {

  /**
   * 指定查询结果是否存入二级缓存，仅对查询方法有效
   * @return
   */
  boolean useCache() default true;

  /**
   * 执行方法后是否刷新缓存
   * @return
   */
  boolean flushCache() default false;

  /**
   * 指定JDBC结果集的类型，仅对查询方法生效
   * - FORWARD_ONLY：仅能向前遍历结果集，性能最优；
   * - SCROLL_INSENSITIVE：可双向遍历，对数据库修改不敏感；
   * - SCROLL_SENSITIVE：可双向遍历，对数据库修改敏感。
   * @return
   */
  ResultSetType resultSetType() default ResultSetType.FORWARD_ONLY;

  /**
   * 指定 JDBC 执行 SQL 的语句类型，对所有方法生效
   * - PREPARED（默认）：使用PreparedStatement，支持参数占位符#{}，防止 SQL 注入，性能优；
   * - STATEMENT：使用Statement，仅支持静态 SQL，无参数绑定；
   * - CALLABLE：使用CallableStatement，用于执行数据库存储过程。
   * @return
   */
  StatementType statementType() default StatementType.PREPARED;

  /**
   * 指定JDBC驱动每次从数据库抓取的结果集行数，仅对查询方法生效
   * -1 表示使用驱动的默认值，设置为正整数时（如 100），驱动会批量抓取数据，减少网络交互。
   * @return
   */
  int fetchSize() default -1;

  /**
   * 指定SQL执行的超时时间
   * -1 表示使用数据库驱动的默认超时时间，设置为正整数时，若 SQL 执行超过该时间，会抛出SQLTimeoutException。
   * @return
   */
  int timeout() default -1;

  /**
   * 指定是否使用数据库自增主键生成策略，仅对插入方法生效
   * @return
   */
  boolean useGeneratedKeys() default false;

  /**
   * 指定实体类中存储自增主键的属性名
   * @return
   */
  String keyProperty() default "id";

  /**
   * 指定数据库中的自增主键列名，一致时可以省略
   * @return
   */
  String keyColumn() default "";
}
