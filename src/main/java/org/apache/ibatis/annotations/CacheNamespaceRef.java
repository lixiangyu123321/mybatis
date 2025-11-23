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
 * **** 就是多个Mapper接口共享一个HashMap ****
 * 实现多 Mapper 接口共享二级缓存命名空间的核心注解
 * 用于替代 XML 映射文件中的 <cache-ref> 标签。
 * 它的核心目标是打破单个 Mapper 对应单个缓存空间的默认规则，让多个 Mapper 复用同一个二级缓存配置，实现缓存数据的共享与一致性。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheNamespaceRef {

  /**
   * **** 其实就是指定配置了二级缓存的Mapper接口的Class对象 ****
   * 指定被引用的Mapper接口的Class对象
   * @return
   */
  Class<?> value();
}
