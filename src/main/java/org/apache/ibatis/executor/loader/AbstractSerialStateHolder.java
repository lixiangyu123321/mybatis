/*
 * Copyright 2013 MyBatis.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.executor.loader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;

/**
 * MyBatis懒加载对象的序列化保险箱
 * 它专门解决「带懒加载属性的对象」在 JDK 序列化 / 反序列化时的核心问题：
 * 既要把对象存起来（序列化），又要保留懒加载的能力，反序列化后还能正常触发懒加载。
 *
 * 核心背景：为什么需要这个类？
 * 之前我们讲过：带懒加载的对象（比如 User 代理对象）直接用 JDK 序列化会出问题 ——
 * 懒加载的ResultLoader、Executor是transient（瞬态）的，序列化时会丢失；
 * 反序列化后，对象虽然还在，但懒加载的任务（LoadPair）没了，调用user.getOrder()会直接返回 null，没法触发查库。
 * AbstractSerialStateHolder的核心就是：自定义序列化规则，把懒加载相关的所有关键信息都存下来，反序列化后还原，让懒加载能力不丢失。
 *
 * 序列化阶段：
 * User 代理对象被包装进AbstractSerialStateHolder；
 * 调用writeExternal，把 User 对象、order/role 的懒加载任务、工具打成字节包；
 * 字节包被序列化（比如存到 Redis）。
 * 反序列化阶段：
 * 从 Redis 读取字节包，调用readExternal，把字节包存到userBeanBytes；
 * 调用readResolve，拆字节包还原 User 对象和懒加载任务；
 * 调用createDeserializationProxy，重建带懒加载能力的 User 代理对象；
 * 你拿到反序列化后的 User 对象，调用user.getOrder()，依然能触发懒加载查库。
 *
 * “保险箱”（AbstractSerialStateHolder）只保存了懒加载的 “静态数据”（比如任务清单、对象属性），
 * 但懒加载的 “动态执行环境”（比如 Executor、数据库连接、线程上下文）是无法被序列化的，
 * 就算想存也存不了 —— 这些才是真正丢失的 “原生环境”。
 */
public abstract class AbstractSerialStateHolder implements Externalizable {

  private static final long serialVersionUID = 8940388717901644661L;
  private static final ThreadLocal<ObjectOutputStream> stream = new ThreadLocal<ObjectOutputStream>();
  private byte[] userBeanBytes = new byte[0];
  private Object userBean;
  private Map<String, ResultLoaderMap.LoadPair> unloadedProperties;
  private ObjectFactory objectFactory;
  private Class<?>[] constructorArgTypes;
  private Object[] constructorArgs;

  public AbstractSerialStateHolder() {
  }

  public AbstractSerialStateHolder(
          final Object userBean,
          final Map<String, ResultLoaderMap.LoadPair> unloadedProperties,
          final ObjectFactory objectFactory,
          List<Class<?>> constructorArgTypes,
          List<Object> constructorArgs) {
    this.userBean = userBean;
    this.unloadedProperties = new HashMap<String, ResultLoaderMap.LoadPair>(unloadedProperties);
    this.objectFactory = objectFactory;
    this.constructorArgTypes = constructorArgTypes.toArray(new Class<?>[constructorArgTypes.size()]);
    this.constructorArgs = constructorArgs.toArray(new Object[constructorArgs.size()]);
  }

  @Override
  public final void writeExternal(final ObjectOutput out) throws IOException {
    boolean firstRound = false;
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream os = stream.get();
    if (os == null) {
      os = new ObjectOutputStream(baos);
      firstRound = true;
      stream.set(os);
    }

    os.writeObject(this.userBean);
    os.writeObject(this.unloadedProperties);
    os.writeObject(this.objectFactory);
    os.writeObject(this.constructorArgTypes);
    os.writeObject(this.constructorArgs);

    final byte[] bytes = baos.toByteArray();
    out.writeObject(bytes);

    if (firstRound) {
      stream.remove();
    }
  }

  @Override
  public final void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final Object data = in.readObject();
    if (data.getClass().isArray()) {
      this.userBeanBytes = (byte[]) data;
    } else {
      this.userBean = data;
    }
  }

  @SuppressWarnings("unchecked")
  protected final Object readResolve() throws ObjectStreamException {
    /* Second run */
    if (this.userBean != null && this.userBeanBytes.length == 0) {
      return this.userBean;
    }

    /* First run */
    try {
      final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(this.userBeanBytes));
      this.userBean = in.readObject();
      this.unloadedProperties = (Map<String, ResultLoaderMap.LoadPair>) in.readObject();
      this.objectFactory = (ObjectFactory) in.readObject();
      this.constructorArgTypes = (Class<?>[]) in.readObject();
      this.constructorArgs = (Object[]) in.readObject();
    } catch (final IOException ex) {
      throw (ObjectStreamException) new StreamCorruptedException().initCause(ex);
    } catch (final ClassNotFoundException ex) {
      throw (ObjectStreamException) new InvalidClassException(ex.getLocalizedMessage()).initCause(ex);
    }

    final Map<String, ResultLoaderMap.LoadPair> arrayProps = new HashMap<String, ResultLoaderMap.LoadPair>(this.unloadedProperties);
    final List<Class<?>> arrayTypes = Arrays.asList(this.constructorArgTypes);
    final List<Object> arrayValues = Arrays.asList(this.constructorArgs);

    return this.createDeserializationProxy(userBean, arrayProps, objectFactory, arrayTypes, arrayValues);
  }

  protected abstract Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
          List<Class<?>> constructorArgTypes, List<Object> constructorArgs);
}
