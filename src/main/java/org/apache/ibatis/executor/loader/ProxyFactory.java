/*
 *    Copyright 2012 the original author or authors.
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
package org.apache.ibatis.executor.loader;

import java.util.List;
import java.util.Properties;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;

/**
 * 是 MyBatis 中定义的动态代理工厂核心接口，它的核心作用是为 MyBatis 的延迟加载（懒加载）功能创建代理对象 ——
 * 当查询结果包含关联对象（如 User 关联 Order）且开启懒加载时，MyBatis 不会立即加载关联对象，
 * 而是通过该接口创建一个代理对象返回，只有当真正访问关联对象的属性时，才会触发实际的数据库查询。
 *
 * 属性配置：通过setProperties接收自定义配置（如代理方式、懒加载触发条件等）；
 * 创建代理对象：createProxy是核心方法，接收懒加载所需的上下文参数，生成目标对象的动态代理；
 * 核心适配：MyBatis 内置了两种实现（JavassistProxyFactory、JdkDynamicProxyFactory），分别基于 Javassist 和 JDK 动态代理创建代理对象，适配不同场景。
 */
public interface ProxyFactory {

  void setProperties(Properties properties);

  /**
   *     Object target, // 被代理的目标对象（懒加载的关联对象，初始为null或空）
   *     ResultLoaderMap lazyLoader, // 懒加载器映射：存储需要延迟加载的属性及对应的加载逻辑
   *     Configuration configuration, // MyBatis全局配置（获取ObjectFactory、TypeHandler等）
   *     ObjectFactory objectFactory, // 对象工厂：用于创建代理对象的实例（兼容不同对象创建方式）
   *     List<Class<?>> constructorArgTypes, // 目标对象构造方法的参数类型列表（创建代理时复用）
   *     List<Object> constructorArgs // 目标对象构造方法的参数值列表（创建代理时复用）
   *
   *     参数名	核心作用	懒加载场景示例
   * target	被代理的原始对象（懒加载初始状态下，该对象通常为 null，代理对象会替代它）	User 关联的 Order 对象，初始为 null，返回的是 Order 的代理对象
   * lazyLoader	懒加载核心：ResultLoaderMap存储了 “属性名 - 加载器” 的映射，比如order属性对应一个ResultLoader，该加载器包含查询 Order 的 SQL 和参数，只有访问user.getOrder().getId()时才会触发ResultLoader执行查询	当调用proxyOrder.getId()时，lazyLoader会执行select * from order where user_id=?
   * configuration	MyBatis 全局配置，提供创建代理所需的核心组件（如 TypeHandlerRegistry、ObjectWrapperFactory）	用于获取懒加载相关的配置（如lazyLoadTriggerMethods）
   * objectFactory	MyBatis 的对象工厂，用于创建代理对象的实例（替代直接 new，支持自定义对象创建逻辑）	创建 Order 代理对象时，通过objectFactory.create(Order.class)实例化
   * constructorArgTypes	目标对象构造方法的参数类型（比如 Order 有构造方法Order(Long id)，则该列表为[Long.class]）	代理对象创建时，复用目标对象的构造方法，保证实例化逻辑一致
   * constructorArgs	目标对象构造方法的参数值（对应上面的类型，比如[1001L]）	与constructorArgTypes配合，调用构造方法创建实例
   * @param target
   * @param lazyLoader
   * @param configuration
   * @param objectFactory
   * @param constructorArgTypes
   * @param constructorArgs
   * @return
   */
  Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);
  
}
