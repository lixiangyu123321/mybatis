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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.mapping.FetchType;

/**
 * **** 基于Setter的实体类映射规则 ****
 * 是 MyBatis 中处理一对多（One-to-Many）关联查询的核心注解，
 * 用于定义 “主表数据” 到 “多张从表数据” 的嵌套查询规则。
 * 它本身不能单独使用，需作为@Result注解的many属性（Many.class类型）的取值，配合@Result完成一对多关联的结果映射
 * （例如：一个用户对应多个订单、一个部门对应多个员工）。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Many {

  /**
   * 指定关联查询的Mapper语句ID
   * （格式为全限定类名.方法名，如com.mapper.OrderMapper.selectByUserId）
   * MyBatis 会将主查询的某个字段值作为参数传入该 Mapper 方法，执行查询并返回集合类型的关联数据（如List<Order>）。
   * **** 关键约束：该 Mapper 方法必须接收一个与主查询关联字段类型匹配的参数（如用户 ID），且返回值必须为Collection/List等集合类型。****
   * @return
   */
  String select() default "";

  /**
   * public enum FetchType {
   *     LAZY,   // 懒加载
   *     EAGER,  // 立即加载
   *     DEFAULT // 跟随全局配置
   * }
   * 指定关联数据的加载策略
   * 可选值为EAGER（立即加载）和LAZY（懒加载），默认值DEFAULT表示跟随 MyBatis 全局配置的懒加载规则（全局配置lazyLoadingEnabled默认为false，即默认立即加载）。
   * - EAGER：查询主表数据时，立即执行关联查询，加载所有从表数据；
   * - LAZY：查询主表数据时不加载关联数据，仅在首次访问关联属性时才执行关联查询（懒加载，提升查询性能）
   *
   * N+1 查询问题：@Many的嵌套查询会触发N+1 查询（1 次主查询 + N 次从表查询，N 为主查询的结果数），
   * 若主查询返回大量数据，会导致数据库执行多次查询，性能下降。优化方案：使用联表查询（JOIN）替代嵌套查询，
   * 直接通过一条 SQL 获取主从表数据，再通过@Result映射为一对多集合。
   * @return
   */
  FetchType fetchType() default FetchType.DEFAULT;

}
