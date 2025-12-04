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
package org.apache.ibatis.binding;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
/**
 * 是MyBatis框架动态代理实现Mapper接口的核心类
 * **** 实现JDK动态代理的InvocationHandler接口 ****
 * 泛型参数 T：表示当前代理的 Mapper 接口的类型（例如 UserMapper、OrderMapper），通过泛型实现对任意 Mapper 接口的代理。
 * 实现 Serializable：标记该类可序列化，主要为了满足分布式场景下代理对象的序列化需求（如 RPC 传输），并定义了序列化版本号 serialVersionUID 保证序列化兼容性。
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;
  /**
   * MyBatis的核心会话对象，提供增删改查的底层API
   */
  private final SqlSession sqlSession;
  /**
   * 当前代理的Mapper接口的Class对象，用于解析接口的方法元信息
   */
  private final Class<T> mapperInterface;
  /**
   * 方法缓存，键是Mapper接口的Method对象，值是对应的MapperMethod对象，避免每次调用方法重复解析
   */
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /**
   * 对于Object继承来的方法，直接执行
   * 对于SQL执行方法。通过mapperMethod进行执行
   * @return
   * @throws Throwable
   */
  //@Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (Object.class.equals(method.getDeclaringClass())) {
      try {
        return method.invoke(this, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    return mapperMethod.execute(sqlSession, args);
  }

  /**
   * 缓存对应的MapperMethod
   * @param method
   * @return
   */
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

}
