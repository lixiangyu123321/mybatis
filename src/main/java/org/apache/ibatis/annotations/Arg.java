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

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * **** 在生成实体类的时候，总要基于一种哦方式去创建一条记录对应的实体类吧 ****
 * **** 与Spring的Bean的实例化一样，同样可以通过构造器注入/setter注入对应属性 ****
 * **** 这个参数与ConstructorArgs一起使用，来指定通过构造器注入一个实体类的映射规则 ****
 * mybatis提供的注解式结果映射核心注解
 * 专门用于配置实体类构造器参数的映射规则。
 * 它是 XML 映射文件中 <arg> 标签的注解等价实现，
 * 与 @ConstructorArgs 注解配合使用，
 * 完成构造器注入式的结果集映射（区别于通过 setter 方法注入的 @Result 注解）。
 * 实际是因为 MyBatis 的注解式映射是通过 Mapper 接口的方法关联的，@Arg 作为 @ConstructorArgs 的数组元素，最终间接作用于 Mapper 接口的查询方法，并非直接标注在普通方法上。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Arg {

  /**
   * 标记该构造器参数是否对应数据库表的主键列
   * 设置为true时，mybatis会将其视为逐渐，用于缓存，关联查询的主键匹配等场景
   * @return
   */
  boolean id() default false;

  /**
   * 指定映射的数据库列名（或列的别名），若为空，会尝试根据构造器参数自动匹配列名（需要开启相关配置，通常需要显示指定）
   * @return
   */
  String column() default "";

  /**
   * 指定构造器参数的Java类型
   * 默认值void.class表示Mybatis会自动类型推断，根据构造器的参数类型或者结果集的列类型
   * @return
   */
  Class<?> javaType() default void.class;

  /**
   * 指定构造器参数的JDBC类型
   * 默认值UNDEFINED表示不指定，根据数据库返回结果自动适配
   * 仅在处理NULL值或者类型歧义时需显式指定（如区分NULL的VARCHAR和INTEGER）
   * @return
   */
  JdbcType jdbcType() default JdbcType.UNDEFINED;

  /**
   * 指定类处理器，用于实现Java类型与JDBC类型的自定义转换
   * 默认值时Mybaits的默认类型处理器，会根据实际类型自动选择对应的具体处理器（如 StringTypeHandler、IntegerTypeHandler）；
   * 若需自定义转换（如枚举、日期格式化），可指定自定义的 TypeHandler 实现类。
   * @return
   */
  Class<? extends TypeHandler<?>> typeHandler() default UnknownTypeHandler.class;

  /**
   * 指定关联查询的Mapper语句ID，用于实现嵌套查询
   * 例如 select = "com.mapper.UserMapper.selectById"，
   * 表示该构造器参数的值需要通过调用这个 Mapper 方法查询得到，常用于一对一、一对多的关联映射。
   * @return
   */
  String select() default "";

  /**
   * 指定关联查询的结果映射ID
   * @return
   */
  String resultMap() default "";
}
