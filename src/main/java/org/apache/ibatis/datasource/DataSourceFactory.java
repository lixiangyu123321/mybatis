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
package org.apache.ibatis.datasource;

import java.util.Properties;
import javax.sql.DataSource;

/**
 * MyBatis 中定义的数据源工厂接口，它遵循「工厂模式」设计，是 MyBatis 对接各种数据源（如 JNDI、POOLED、UNPOOLED）的核心扩展点 ——
 * 所有数据源实现都必须通过这个接口暴露，保证 MyBatis 能以统一的方式创建和获取DataSource实例。
 *
 * 定义统一的数据源创建规范：不管是连接池数据源（如 POOLED）、非连接池数据源（如 UNPOOLED），还是 JNDI 数据源（如你之前问的JndiDataSourceFactory），都必须实现该接口；
 * 解耦数据源配置与使用：MyBatis 上层只依赖这个接口，不关心具体数据源的实现细节，便于扩展新的数据源类型；
 * 标准化配置传递：通过setProperties方法接收配置属性（如数据库 URL、用户名、连接池大小），保证所有数据源的配置方式一致。
 */
public interface DataSourceFactory {

  /**
   * 参数类型：Properties是 Java 标准的键值对集合，兼容 MyBatis 配置文件中<dataSource>标签下的<property>配置（如 XML 中配置的属性会被封装为Properties传入）；
   * 执行时机：MyBatis 初始化数据源时，会先调用该方法传递配置，再调用getDataSource()获取数据源实例；
   * 实现要求：不同数据源工厂需在该方法中解析自身所需的配置（如 POOLED 数据源解析poolMaximumActiveConnections，JNDI 数据源解析data_source）；
   * @param props
   */
  void setProperties(Properties props);

  /**
   * 返回值规范：必须返回javax.sql.DataSource的实现类（如PooledDataSource、JdbcDataSource、容器提供的 JNDI 数据源），保证与 Java JDBC 规范兼容；
   * 执行时机：在setProperties之后调用，此时数据源已完成配置初始化；
   * 核心约束：返回的DataSource必须是可用的（如连接池已初始化、JNDI 数据源已成功查找），否则会导致后续数据库操作失败；
   * MyBatis 的使用方式：MyBatis 的Environment会通过该方法获取数据源，进而创建Transaction和Connection：
   * @return
   */
  DataSource getDataSource();

}
