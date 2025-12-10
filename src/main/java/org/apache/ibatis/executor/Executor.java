/*
 *    Copyright 2009-2014 the original author or authors.
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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * 核心执行器接口，它定义了 MyBatis 执行 SQL 操作（查询、更新、事务管理、缓存管理等）的所有核心行为规范。
 * MyBatis 会为不同的执行策略（如简单执行器、重用执行器、批量执行器）提供该接口的实现类（如 SimpleExecutor、ReuseExecutor、BatchExecutor）。
 */
public interface Executor {

  /**
   * 自定义处理SQL查询结果的接口， 若传入null，MyBatis会用默认方式将结果封装为List返回
   */
  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 执行增 / 删 / 改类型的 SQL 语句（INSERT/UPDATE/DELETE）
   * @param ms 封装了 Mapper 中一个 SQL 节点的所有信息（如 SQL 语句、参数映射、返回值类型、缓存配置等）
   * @param parameter SQL 语句的入参（如 POJO、Map、基本类型等）
   * @return
   * @throws SQLException
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 执行查询操作的底层核心方法，其他查询方法最终都会调用该方法。
   * @param ms 封装 SQL 元信息；
   * @param parameter SQL 入参；
   * @param rowBounds 分页参数（MyBatis 内存分页，包含 offset 起始行、limit 行数）；
   * @param resultHandler 结果处理器，自定义处理查询结果（如逐行处理，避免一次性加载大量数据到内存）；
   * @param cacheKey 缓存键，用于二级缓存的索引（MyBatis 根据 SQL、参数、分页等信息生成唯一缓存键）；
   * @param boundSql 封装了最终要执行的 SQL 语句（已解析占位符、参数）和参数映射信息。
   * @return 列表
   * @param <E>
   * @throws SQLException
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  /**
   * 会在内部自动生成 CacheKey 和 BoundSql，然后调用上面的完整参数版 query 方法。
   * @param ms 封装 SQL 元信息；
   * @param parameter SQL 入参；
   * @param rowBounds 分页参数（MyBatis 内存分页，包含 offset 起始行、limit 行数）；
   * @param resultHandler 结果处理器，自定义处理查询结果（如逐行处理，避免一次性加载大量数据到内存）；
   * @return
   * @param <E>
   * @throws SQLException
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  /**
   * 刷新（执行）批量操作的 SQL 语句，返回批量执行结果。
   * 仅在 BatchExecutor（批量执行器）中有效，用于将批量缓存的 SQL 一次性提交到数据库执行。
   * @return 每个 BatchResult 封装一个批量操作的结果（如受影响行数、对应的 MappedStatement 等）。
   * @throws SQLException
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 作用：提交事务。
   * 参数：required 表示 “是否强制提交”：
   * true：无论事务是否有修改，都尝试提交；
   * false：仅当事务有修改时才提交（避免空提交）。
   * @param required
   * @throws SQLException
   */
  void commit(boolean required) throws SQLException;

  /**
   * 作用：回滚事务。
   * 参数：required 表示 “是否强制回滚”，逻辑与 commit 一致。
   * @param required
   * @throws SQLException
   */
  void rollback(boolean required) throws SQLException;

  /**
   * 根据 SQL 元信息、参数、分页、最终 SQL 生成唯一的 CacheKey（用于一级 / 二级缓存的索引）。
   * @param ms SQL元信息
   * @param parameterObject 参数信息
   * @param rowBounds 分页参数
   * @param boundSql 解析了占位符的SQL语句与参数映射关系
   * @return
   */
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 作用：检查指定 CacheKey 对应的结果是否已存在于缓存中（一级 / 二级缓存）。
   * 返回值：true 表示缓存命中，false 表示未命中。
   * @param ms SQL元信息（SQL节点的所有信息）
   * @param key 键值
   * @return
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  /**
   * 作用：清空一级缓存（本地缓存，默认会话级别）。
   * 场景：MyBatis 一级缓存默认开启，执行更新操作后会自动调用该方法，避免缓存与数据库数据不一致。
   */
  void clearLocalCache();

  /**
   * 处理 MyBatis 的延迟加载（懒加载） 逻辑。
   * 当查询关联对象时，MyBatis 不会立即执行关联查询，而是通过该方法记录懒加载任务，直到用户真正访问该属性时才执行查询。
   * @param ms SQL元信息
   * @param resultObject 反射工具类，为设置信息提供同意入口
   * @param property 需要懒加载的属性名
   * @param key 懒加载查询的缓存键
   * @param targetType 懒加载属性的类型
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取当前执行器关联的 Transaction（事务对象）。
   * @return Transaction 是 MyBatis 封装数据库事务的接口，底层对接 JDBC 的 Connection。
   */
  Transaction getTransaction();

  /**
   * 关闭执行器，释放资源（如数据库连接）。
   * @param forceRollback 参数：forceRollback 表示关闭前是否强制回滚未提交的事务：
   * true：关闭前回滚所有未提交的事务；
   * false：尝试提交事务后再关闭。
   */
  void close(boolean forceRollback);

  /**
   * 检查执行器是否关闭
   * @return
   */
  boolean isClosed();

  /**
   * 作用：为当前执行器设置一个 “包装器” 执行器（MyBatis 的装饰器模式应用）。
   * 场景：MyBatis 会通过包装器增强执行器功能，如 CachingExecutor 包装普通执行器，添加二级缓存功能；ReuseExecutor 包装后可重用 Statement 对象。
   * @param executor
   */
  void setExecutorWrapper(Executor executor);

}
