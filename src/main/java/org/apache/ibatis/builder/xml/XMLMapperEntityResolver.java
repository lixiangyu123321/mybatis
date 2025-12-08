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
package org.apache.ibatis.builder.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 这个类是 MyBatis 自定义的 XML 实体解析器，
 * 核心作用是拦截 MyBatis 配置文件 / 映射文件解析时对外部 DTD 文件的网络请求，
 * 转而从本地类路径加载 DTD 文件，提升解析效率并避免网络依赖 / 安全风险。
 *
 * 说白了，就是解析到外部引用：
 * <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
 * 不从网络中下载，直接从本地加载就行
 */
public class XMLMapperEntityResolver implements EntityResolver {

  /**
   * 这部分定义了 MyBatis/IBatis（MyBatis 前身）的 DTD 公共标识（publicId）和系统标识（systemId），以及对应的本地 DTD 文件路径：
   *
   * publicId/systemId：XML 文件中 DOCTYPE 标签里的两个核心标识，示例：
   * 逻辑标识：全局唯一ID呗
   * 系统标识：DTD的“具体位置”
   * <!-- Mapper 文件的 DOCTYPE 示例 -->
   * <!DOCTYPE mapper
   *   PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"  <!-- publicId -->
   *   "http://mybatis.org/dtd/mybatis-3-mapper.dtd"> <!-- systemId -->
   */
  private static final Map<String, String> doctypeMap = new HashMap<String, String>();

  private static final String IBATIS_CONFIG_PUBLIC = "-//ibatis.apache.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String IBATIS_CONFIG_SYSTEM = "http://ibatis.apache.org/dtd/ibatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

  private static final String IBATIS_MAPPER_PUBLIC = "-//ibatis.apache.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String IBATIS_MAPPER_SYSTEM = "http://ibatis.apache.org/dtd/ibatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

  private static final String MYBATIS_CONFIG_PUBLIC = "-//mybatis.org//DTD Config 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String MYBATIS_CONFIG_SYSTEM = "http://mybatis.org/dtd/mybatis-3-config.dtd".toUpperCase(Locale.ENGLISH);

  private static final String MYBATIS_MAPPER_PUBLIC = "-//mybatis.org//DTD Mapper 3.0//EN".toUpperCase(Locale.ENGLISH);
  private static final String MYBATIS_MAPPER_SYSTEM = "http://mybatis.org/dtd/mybatis-3-mapper.dtd".toUpperCase(Locale.ENGLISH);

  /**
   * // MyBatis 配置文件的本地 DTD 文件路径（类路径下）
   * private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
   * // MyBatis Mapper 文件的本地 DTD 文件路径
   * private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";
   */
  private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
  private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

  static {
    doctypeMap.put(IBATIS_CONFIG_SYSTEM, MYBATIS_CONFIG_DTD);
    doctypeMap.put(IBATIS_CONFIG_PUBLIC, MYBATIS_CONFIG_DTD);

    doctypeMap.put(IBATIS_MAPPER_SYSTEM, MYBATIS_MAPPER_DTD);
    doctypeMap.put(IBATIS_MAPPER_PUBLIC, MYBATIS_MAPPER_DTD);

    doctypeMap.put(MYBATIS_CONFIG_SYSTEM, MYBATIS_CONFIG_DTD);
    doctypeMap.put(MYBATIS_CONFIG_PUBLIC, MYBATIS_CONFIG_DTD);

    doctypeMap.put(MYBATIS_MAPPER_SYSTEM, MYBATIS_MAPPER_DTD);
    doctypeMap.put(MYBATIS_MAPPER_PUBLIC, MYBATIS_MAPPER_DTD);
  }

  /**
   * InputSource相当于数据源适配器
   * XML/DTD 数据可能来自 InputStream（字节流）、Reader（字符流）、URL（网络地址）等不同来源，InputSource 把这些来源封装成统一的接口；
   * @param publicId The public identifier of the external entity
   *        being referenced, or null if none was supplied.
   * @param systemId The system identifier of the external entity
   *        being referenced.
   * @return
   * @throws SAXException
   */
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {

    if (publicId != null) {
      publicId = publicId.toUpperCase(Locale.ENGLISH);
    }
    if (systemId != null) {
      systemId = systemId.toUpperCase(Locale.ENGLISH);
    }

    InputSource source = null;
    try {
      // 都是获得本地地址
      String path = doctypeMap.get(publicId);
      source = getInputSource(path, source);
      if (source == null) {
        // 都是获得DTD的本地地址
        path = doctypeMap.get(systemId);
        source = getInputSource(path, source);
      }
    } catch (Exception e) {
      throw new SAXException(e.toString());
    }
    return source;
  }

  /**
   * 用于辅助从本地加载DTD文件
   * @param path
   * @param source
   * @return
   */
  private InputSource getInputSource(String path, InputSource source) {
    if (path != null) {
      InputStream in;
      try {
        // 加载本地DTD文件为字节流
        in = Resources.getResourceAsStream(path);
        source = new InputSource(in);
      } catch (IOException e) {
      }
    }
    return source;
  }

}