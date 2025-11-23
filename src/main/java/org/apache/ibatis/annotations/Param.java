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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * **** 说白了，就是定义SQL中可以使用的参数别名 ****
 * **** 也就是说， 多参数最好加上该注解 ****
 * MyBatis 中为 Mapper 接口方法的参数指定别名的核心注解
 * 它的核心解决了两个关键问题：一是 Java 反射在编译后可能丢失方法参数名（尤其是未开启-parameters编译选项时），
 * 导致 MyBatis 无法识别 SQL 中的参数占位符；二是多参数传递时，明确每个参数的名称，让 SQL 能精准引用。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Param {
  /**
   * 指定参数的别名
   * **** MyBatis 会将该别名作为键，参数值作为值，存入内部的参数映射容器（ParamMap）。SQL 中通过#{别名}引用该参数，而非方法的原参数名。****
   * 约束：别名需符合 Java 标识符规范，且同一方法的多个参数的别名不能重复。
   *
   * **** 单参数的使用规则 ****
   * **** 多参数的特殊规则 ****
   * 单参数的特殊处理
   * 单参数为实体类 / Map时，无需使用@Param，MyBatis 可直接通过实体属性名 / Map 的key引用参数（如#{username}对应实体的username属性）；
   * 单参数为List/Array时，若不使用@Param，MyBatis 会为其分配默认别名：List的默认别名为list，数组的默认别名为array（如<foreach collection="list">）。
   * 编译选项的影响
   * Java 编译时添加-parameters选项（如javac -parameters UserMapper.java），编译器会保留方法的参数名，此时未标注@Param的单参数可直接通过原参数名引用；
   * 若未开启该选项，未标注@Param的多参数只能通过param1、param2等有序别名引用（不推荐，可读性差）。
   * 别名的唯一性
   * 同一 Mapper 方法的多个参数的@Param别名不能重复，否则 MyBatis 会覆盖前序参数的值，导致参数绑定错误。
   * 与动态 SQL 的配合
   * 使用<foreach>遍历集合 / 数组时，collection属性必须与@Param的别名一致（或默认别名），这是批量操作的核心，若匹配错误会抛出BindingException。
   *
   * @return
   */
  String value();
}
