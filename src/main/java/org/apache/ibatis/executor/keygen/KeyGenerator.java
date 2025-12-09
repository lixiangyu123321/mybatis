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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;


/**
 * 是 MyBatis 中定义的主键生成器核心接口，它遵循「策略模式」设计，是 MyBatis 所有主键生成逻辑的统一扩展点 ——
 * 不管是 MySQL 的自增主键（Jdbc3KeyGenerator）、Oracle 的序列主键（SequenceKeyGenerator），
 * 还是自定义主键生成规则，都必须实现这个接口，保证 MyBatis 能以统一的时机触发主键生成 / 回填逻辑。
 */
public interface KeyGenerator {

  /**
   *
   * @param executor MyBatis 的执行器（负责 SQL 执行），可用于获取数据库连接、执行预查询（如获取序列值）
   * @param ms 	映射语句对象（包含 Mapper 配置：主键属性、SQL 语句、配置信息等）
   * @param stmt 待执行的 JDBC Statement（此时尚未执行 INSERT，仅初始化完成）
   * @param parameter INSERT 的参数对象（如 User 实体），用于设置生成的主键值
   */
  void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

  void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter);

}
