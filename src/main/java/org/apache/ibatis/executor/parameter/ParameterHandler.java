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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 专门负责给 JDBC 的 PreparedStatement 设置 SQL 参数的核心接口——
 * 简单说，它就是 MyBatis 和 JDBC 之间的 “参数翻译官”：把 Mapper 方法传入的参数（比如user.getId()），
 * 按照 SQL 中的参数占位符（?）的顺序，逐个设置到 PreparedStatement 中，让 SQL 能正确执行。
 *
 * 一、先补基础：为什么需要这个接口？
 * JDBC 执行带参数的 SQL 时，需要用PreparedStatement.setXxx()方法手动设置参数（比如setLong(1, 1001)、setString(2, "张三")），这个过程有两个痛点：
 * 手动设置参数繁琐，还要匹配参数类型（比如 Long 对应 setLong，String 对应 setString）；
 * Mapper 方法的参数可能是单个值、Map、实体类等多种形式，需要统一的逻辑解析参数。
 * ParameterHandler就是为了解决这两个问题：它封装了 “解析参数→设置参数” 的全流程，让 MyBatis 能自动、统一地给 SQL 设置参数。
 *
 * 解析 Mapper 方法，生成 SQL：SELECT * FROM user WHERE id = ? AND name = ?；
 * 创建ParameterHandler实例，传入参数集合（id=1001，name = 张三）；
 * 创建 JDBC 的 PreparedStatement 对象；
 * 调用parameterHandler.setParameters(ps)：
 * 解析参数集合，拿到 id=1001、name = 张三；
 * 按顺序调用ps.setLong(1, 1001)、ps.setString(2, "张三")；
 * 执行 PreparedStatement，获取结果集。
 */
public interface ParameterHandler {

  Object getParameterObject();

  void setParameters(PreparedStatement ps)
      throws SQLException;

}
