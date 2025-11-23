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
 * 定义插入操作 SQL 语句的核心注解
 * 存储删除操作的 SQL 语句，支持数组形式的原因有二：
 * 1. SQL 语句换行拆分：将长 SQL 按行拆分为多个字符串元素，提升代码可读性（MyBatis 会自动拼接数组元素为完整 SQL）；
 * 2. 动态 SQL 兼容：直接在数组中编写 MyBatis 动态 SQL 标签（如<foreach>、<if>），框架会按动态 SQL 规则解析执行。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Insert {
  String[] value();
}
