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

/**
 * **** 区别与DELETE， 这里以方法的形式提供delete语句 ****
 * **** 拼接SQL语句时需要注意SQL注入 ****
 * **** 使用场景 ****
 * 按不同业务条件动态拼接删除条件（如普通用户按 ID 删除、管理员按角色批量删除）；
 * 动态指定删除的表名 / 列名（需做 SQL 注入防护）；
 * 批量删除的复杂逻辑（如先删子表、再删主表，生成多语句 SQL，需数据库支持）。
 * 是 MyBatis 中动态生成删除操作 SQL 的核心注解，
 * 属于SQL 提供者（Provider）注解体系的一员（同体系还有@SelectProvider、@InsertProvider、@UpdateProvider）。
 * 它与@Delete的核心区别是：@Delete直接写死 SQL 语句，而@DeleteProvider通过指定外部类和方法动态生成删除 SQL，适用于 SQL 逻辑复杂、需根据业务动态拼接的场景。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DeleteProvider {

  /**
   * 指定提供“删除SQL”的生成类
   * 该类可以是普通的 Java 类（无需实现接口），MyBatis 会通过反射创建该类的实例（默认无参构造），再调用method指定的方法。
   * 1. 类必须有无参构造方法（若需传入参数，可通过 Spring 托管为 Bean，或自定义实例化策略）；
   * 2. 类中必须包含method指定的方法，否则运行时抛出NoSuchMethodException。
   * @return
   */
  Class<?> type();

  /**
   * 指定type类中用于生成删除SQL的方法
   * 该方法的返回值必须为String（即生成的 SQL 语句），参数可根据业务需求接收 Mapper 方法的参数（支持@Param、实体类、基本类型等）。
   * 1. 方法返回值必须是String，否则 MyBatis 无法解析为 SQL；
   * 2. 方法的参数列表需与 Mapper 接口方法的参数列表兼容（或通过 MyBatis 的参数解析规则匹配）；
   * 3. 方法支持重载，但 MyBatis 会优先匹配参数列表最贴合的方法。
   * @return
   */
  String method();
}
