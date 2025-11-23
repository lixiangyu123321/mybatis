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
 * public interface UserMapper {
 *     // 查询所有用户，以User的id属性作为Map的键，返回Map<Long, User>
 *     @MapKey("id") // 指定实体的id属性为Map的键
 *     @Select("SELECT id, username, age FROM user")
 *     Map<Long, User> selectAllUserAsMap();
 *
 *     // 根据年龄查询用户，以id为键返回Map
 *     @MapKey("id")
 *     @Select("SELECT id, username, age FROM user WHERE age = #{age}")
 *     Map<Long, User> selectUserByAgeAsMap(Integer age);
 * }
 * 指定查询结果转换为Map集合时的键（Key）
 * 它专门标注在 Mapper 接口的方法上，且仅对返回值为java.util.Map类型的方法生效 —— 作用是告诉 MyBatis：
 * 将查询结果集中的每个实体对象，以其指定属性的值作为Map的键，以实体对象本身作为Map的值，从而生成一个键值对形式的Map集合，
 * 替代默认的列表（List）返回，方便根据键快速查找实体。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MapKey {

  /**
   * 指定作为键的实体类属性名
   * 指定作为Map键的实体类属性名（如id、username），MyBatis 会通过该属性的getter方法（如getId()、getUsername()）从每个实体对象中提取值，作为Map的键；
   * 约束：
   * 1. 实体类中必须存在该属性，且有对应的getter方法（遵循 JavaBean 规范），否则 MyBatis 会抛出NoSuchMethodException；
   * 2. 若需以数据库列名作为键（而非实体属性），需先通过结果映射将列名绑定到实体属性，再指定该属性名。
   *
   * **** MyBatis 对Map的键重复问题采用覆盖策略：****
   * @return
   */
  String value();
}
