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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.CacheBuilder;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
/**
 * 映射构建器助手，建造者模式,继承BaseBuilder
 * 是 MyBatis 在解析 Mapper XML 文件或注解式 Mapper 时的核心辅助类
 *
 * 管理 Mapper 的命名空间（Namespace），确保 SQL 语句、ResultMap 等组件的 ID 在命名空间内唯一；
 * 构建并注册缓存（Cache）和缓存引用（cache-ref）；
 * 构建并注册参数映射（ParameterMap）、结果映射（ResultMap）、鉴别器（Discriminator）；
 * 构建并注册核心执行单元 **MappedStatement**（封装 SQL 语句、参数、结果映射、执行规则等）；
 * 处理命名空间的拼接、类型处理器解析、语言驱动（LanguageDriver）加载等辅助逻辑。
 */
public class MapperBuilderAssistant extends BaseBuilder {

  /**
   * 当前的命名空间
   * 对应 XML 的namespace属性，或注解 Mapper 的全类名
   */
  private String currentNamespace;
  /**
   * 当前解析的Mapper资源路径
   * com/xxx/mapper/ArticleMapper.xml
   */
  private String resource;
  /**
   * 当前Mapper绑定的缓存示例
   */
  private Cache currentCache;
  /**
   * 标记缓存引用是否为解析完成（避免缓存依赖未加载时的异常）
   */
  private boolean unresolvedCacheRef;

  public MapperBuilderAssistant(Configuration configuration, String resource) {
    super(configuration);
    ErrorContext.instance().resource(resource);
    this.resource = resource;
  }

  public String getCurrentNamespace() {
    return currentNamespace;
  }

  /**
   * 设定当前 Mapper 的命名空间，做了两个关键校验：
   * 1. 命名空间不能为空，否则抛出BuilderException；
   * 2. 命名空间不能重复修改（避免解析过程中命名空间不一致）。
   * @param currentNamespace
   */
  public void setCurrentNamespace(String currentNamespace) {
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '"
          + this.currentNamespace + "' but found '" + currentNamespace + "'.");
    }

    this.currentNamespace = currentNamespace;
  }

  /**
   * 为组件 ID 拼接命名空间，生成全局唯一的 ID，核心逻辑：
   * 1. 如果是引用类型（isReference=true，如引用其他命名空间的 ResultMap），且 ID 包含.，则直接返回（已带命名空间）；
   * 2. 如果是当前命名空间内的组件（isReference=false），若 ID 未带命名空间，则拼接currentNamespace + "." + base；
   * 3. 禁止当前命名空间内的组件 ID 包含.（避免与命名空间分隔符冲突）。
   * @param base
   * @param isReference
   * @return
   */
  public String applyCurrentNamespace(String base, boolean isReference) {
    if (base == null) {
      return null;
    }
    if (isReference) {
      if (base.contains(".")) {
        return base;
      }
    } else {
      // is it qualified with this namespace yet?
      if (base.startsWith(currentNamespace + ".")) {
        return base;
      }
      if (base.contains(".")) {
        throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
      }
    }
    return currentNamespace + "." + base;
  }

  /**
   * 解析<cache-ref>标签，引用其他命名空间的缓存：
   * 1. 校验引用的命名空间不能为空；
   * 2. 从Configuration中获取目标命名空间的缓存，若不存在则抛出IncompleteElementException；
   * 3. 标记缓存解析状态，将目标缓存设为当前 Mapper 的currentCache。
   * @param namespace
   * @return
   */
  public Cache useCacheRef(String namespace) {
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      unresolvedCacheRef = true;
      Cache cache = configuration.getCache(namespace);
      if (cache == null) {
        throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
      }
      currentCache = cache;
      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }

  /**
   * 解析<cache>标签，创建新的缓存实例：
   * 为缓存设置默认实现（默认PerpetualCache，淘汰策略默认LruCache）；
   * 通过CacheBuilder构建缓存实例，缓存 ID 为当前命名空间；
   * 将缓存注册到Configuration，并设为当前 Mapper 的currentCache。
   * @param typeClass
   * @param evictionClass
   * @param flushInterval
   * @param size
   * @param readWrite
   * @param blocking
   * @param props
   * @return
   */
  public Cache useNewCache(Class<? extends Cache> typeClass,
      Class<? extends Cache> evictionClass,
      Long flushInterval,
      Integer size,
      boolean readWrite,
      boolean blocking,
      Properties props) {
    typeClass = valueOrDefault(typeClass, PerpetualCache.class);
    evictionClass = valueOrDefault(evictionClass, LruCache.class);
    //调用CacheBuilder构建cache,id=currentNamespace
    Cache cache = new CacheBuilder(currentNamespace)
        .implementation(typeClass)
        .addDecorator(evictionClass)
        .clearInterval(flushInterval)
        .size(size)
        .readWrite(readWrite)
        .blocking(blocking)
        .properties(props)
        .build();
    //加入缓存
    configuration.addCache(cache);
    //当前的缓存
    currentCache = cache;
    return cache;
  }

  /**
   * 构建并注册ParameterMap
   * @param id
   * @param parameterClass
   * @param parameterMappings
   * @return
   */
  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    id = applyCurrentNamespace(id, false);
    ParameterMap.Builder parameterMapBuilder = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings);
    ParameterMap parameterMap = parameterMapBuilder.build();
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }

  /**
   * 构建单个结果映射规则
   * 1 解析属性类型、类型处理器、嵌套查询 / 嵌套结果映射；
   * 2 处理复合列名、列前缀、非空列等特殊配置；
   * 3 返回ResultMapping实例，作为ResultMap的最小组成单元。
   * @param parameterType
   * @param property
   * @param javaType
   * @param jdbcType
   * @param resultMap
   * @param parameterMode
   * @param typeHandler
   * @param numericScale
   * @return
   */
  public ParameterMapping buildParameterMapping(
      Class<?> parameterType,
      String property,
      Class<?> javaType,
      JdbcType jdbcType,
      String resultMap,
      ParameterMode parameterMode,
      Class<? extends TypeHandler<?>> typeHandler,
      Integer numericScale) {
    resultMap = applyCurrentNamespace(resultMap, true);

    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, javaTypeClass);
    builder.jdbcType(jdbcType);
    builder.resultMapId(resultMap);
    builder.mode(parameterMode);
    builder.numericScale(numericScale);
    builder.typeHandler(typeHandlerInstance);
    return builder.build();
  }

  /**
   * 构建并注册ResultMap
   * 1 拼接 ResultMap 的全局唯一 ID；
   * 2 处理 ResultMap 的继承（extend属性）：合并父 ResultMap 的映射规则，若子 ResultMap 包含构造器映射，则移除父类的构造器映射；
   * 3 构建ResultMap实例并注册到Configuration。
   * @param id
   * @param type
   * @param extend
   * @param discriminator
   * @param resultMappings
   * @param autoMapping
   * @return
   */
  public ResultMap addResultMap(
      String id,
      Class<?> type,
      String extend,
      Discriminator discriminator,
      List<ResultMapping> resultMappings,
      Boolean autoMapping) {
    id = applyCurrentNamespace(id, false);
    extend = applyCurrentNamespace(extend, true);

    ResultMap.Builder resultMapBuilder = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping);
    if (extend != null) {
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      ResultMap resultMap = configuration.getResultMap(extend);
      List<ResultMapping> extendedResultMappings = new ArrayList<ResultMapping>(resultMap.getResultMappings());
      extendedResultMappings.removeAll(resultMappings);
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      if (declaresConstructor) {
        Iterator<ResultMapping> extendedResultMappingsIter = extendedResultMappings.iterator();
        while (extendedResultMappingsIter.hasNext()) {
          if (extendedResultMappingsIter.next().getFlags().contains(ResultFlag.CONSTRUCTOR)) {
            extendedResultMappingsIter.remove();
          }
        }
      }
      resultMappings.addAll(extendedResultMappings);
    }
    resultMapBuilder.discriminator(discriminator);
    ResultMap resultMap = resultMapBuilder.build();
    configuration.addResultMap(resultMap);
    return resultMap;
  }

  /**
   * 构建鉴别器（<discriminator>，用于多态结果映射）：
   * 1 先构建鉴别器的列映射（ResultMapping）；
   * 2 为鉴别器的结果映射引用拼接命名空间；
   * 3 通过Discriminator.Builder构建鉴别器实例。
   * @param resultType
   * @param column
   * @param javaType
   * @param jdbcType
   * @param typeHandler
   * @param discriminatorMap
   * @return
   */
  public Discriminator buildDiscriminator(
      Class<?> resultType,
      String column,
      Class<?> javaType,
      JdbcType jdbcType,
      Class<? extends TypeHandler<?>> typeHandler,
      Map<String, String> discriminatorMap) {
    ResultMapping resultMapping = buildResultMapping(
        resultType,
        null,
        column,
        javaType,
        jdbcType,
        null,
        null,
        null,
        null,
        typeHandler,
        new ArrayList<ResultFlag>(),
        null,
        null,
        false);
    Map<String, String> namespaceDiscriminatorMap = new HashMap<String, String>();
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      String resultMap = e.getValue();
      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    Discriminator.Builder discriminatorBuilder = new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap);
    return discriminatorBuilder.build();
  }

  /**
   * 负责构建Mybatis执行SQL的核心载体MappedStatement
   * 对应 Mapper XML 中的<select>、<insert>、<update>、<delete>标签。
   * 1.前置校验：若缓存引用未解析完成，抛出异常；
   * 2.ID 处理：为 SQL 语句 ID 拼接命名空间，生成全局唯一 ID；
   * 3.构建器初始化：通过MappedStatement.Builder设置 SQL 源（SqlSource）、语句类型（StatementType）、SQL 命令类型（SqlCommandType）等基础属性；
   * 4.参数配置：通过setStatementParameterMap设置参数映射（ParameterMap）或参数类型；
   * 5.结果配置：通过setStatementResultMap设置结果映射（ResultMap）或结果类型、结果集类型；
   * 6.缓存配置：通过setStatementCache设置缓存规则（查询语句默认开启缓存，增删改默认刷新缓存）；
   * 7.注册实例：构建MappedStatement并注册到Configuration。
   * @param id
   * @param sqlSource
   * @param statementType
   * @param sqlCommandType
   * @param fetchSize
   * @param timeout
   * @param parameterMap
   * @param parameterType
   * @param resultMap
   * @param resultType
   * @param resultSetType
   * @param flushCache
   * @param useCache
   * @param resultOrdered
   * @param keyGenerator
   * @param keyProperty
   * @param keyColumn
   * @param databaseId
   * @param lang
   * @param resultSets
   * @return
   */
  public MappedStatement addMappedStatement(
      String id,
      SqlSource sqlSource,
      StatementType statementType,
      SqlCommandType sqlCommandType,
      Integer fetchSize,
      Integer timeout,
      String parameterMap,
      Class<?> parameterType,
      String resultMap,
      Class<?> resultType,
      ResultSetType resultSetType,
      boolean flushCache,
      boolean useCache,
      boolean resultOrdered,
      KeyGenerator keyGenerator,
      String keyProperty,
      String keyColumn,
      String databaseId,
      LanguageDriver lang,
      String resultSets) {

    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }
    
    id = applyCurrentNamespace(id, false);
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    // remove test code
    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType);
    statementBuilder.resource(resource);
    statementBuilder.fetchSize(fetchSize);
    statementBuilder.statementType(statementType);
    statementBuilder.keyGenerator(keyGenerator);
    statementBuilder.keyProperty(keyProperty);
    statementBuilder.keyColumn(keyColumn);
    statementBuilder.databaseId(databaseId);
    statementBuilder.lang(lang);
    statementBuilder.resultOrdered(resultOrdered);
    statementBuilder.resulSets(resultSets);
    setStatementTimeout(timeout, statementBuilder);

    setStatementParameterMap(parameterMap, parameterType, statementBuilder);
    setStatementResultMap(resultMap, resultType, resultSetType, statementBuilder);
    setStatementCache(isSelect, flushCache, useCache, currentCache, statementBuilder);

    MappedStatement statement = statementBuilder.build();
    configuration.addMappedStatement(statement);
    return statement;
  }

  /**
   * 为空值提供默认值
   * @param value
   * @param defaultValue
   * @return
   * @param <T>
   */
  private <T> T valueOrDefault(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }


  /**
   * 设置语句缓存配置
   *
   * @param isSelect 是否为查询操作
   * @param flushCache 是否在执行前清空缓存
   * @param useCache 是否使用缓存
   * @param cache 缓存实例
   * @param statementBuilder 语句构建器
   */
  private void setStatementCache(
      boolean isSelect,
      boolean flushCache,
      boolean useCache,
      Cache cache,
      MappedStatement.Builder statementBuilder) {
    flushCache = valueOrDefault(flushCache, !isSelect);
    useCache = valueOrDefault(useCache, isSelect);
    statementBuilder.flushCacheRequired(flushCache);
    statementBuilder.useCache(useCache);
    statementBuilder.cache(cache);
  }

  /**
   * 设置语句参数映射
   *
   * @param parameterMap 参数映射的名称
   * @param parameterTypeClass 参数类型的类
   * @param statementBuilder 语句构建器
   */
  private void setStatementParameterMap(
      String parameterMap,
      Class<?> parameterTypeClass,
      MappedStatement.Builder statementBuilder) {
    parameterMap = applyCurrentNamespace(parameterMap, true);

    if (parameterMap != null) {
      try {
        statementBuilder.parameterMap(configuration.getParameterMap(parameterMap));
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find parameter map " + parameterMap, e);
      }
    } else if (parameterTypeClass != null) {
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      ParameterMap.Builder inlineParameterMapBuilder = new ParameterMap.Builder(
          configuration,
          statementBuilder.id() + "-Inline",
          parameterTypeClass,
          parameterMappings);
      statementBuilder.parameterMap(inlineParameterMapBuilder.build());
    }
  }

  /**
   * 设置Statement的结果映射
   *
   * @param resultMap 结果映射的名称，可以是逗号分隔的多个映射名称
   * @param resultType 结果类型，当没有提供resultMap时使用
   * @param resultSetType 结果集类型
   * @param statementBuilder 用于构建MappedStatement的构建器
   */
  private void setStatementResultMap(
      String resultMap,
      Class<?> resultType,
      ResultSetType resultSetType,
      MappedStatement.Builder statementBuilder) {
    resultMap = applyCurrentNamespace(resultMap, true);

    List<ResultMap> resultMaps = new ArrayList<ResultMap>();
    if (resultMap != null) {
      String[] resultMapNames = resultMap.split(",");
      for (String resultMapName : resultMapNames) {
        try {
          resultMaps.add(configuration.getResultMap(resultMapName.trim()));
        } catch (IllegalArgumentException e) {
          throw new IncompleteElementException("Could not find result map " + resultMapName, e);
        }
      }
    } else if (resultType != null) {
      ResultMap.Builder inlineResultMapBuilder = new ResultMap.Builder(
          configuration,
          statementBuilder.id() + "-Inline",
          resultType,
          new ArrayList<ResultMapping>(),
          null);
      resultMaps.add(inlineResultMapBuilder.build());
    }
    statementBuilder.resultMaps(resultMaps);

    statementBuilder.resultSetType(resultSetType);
  }

  /**
   * 设置语句超时时间
   *
   * @param timeout 超时时间，如果为 null，则使用配置的默认超时时间
   * @param statementBuilder 用于构建语句的 MappedStatement.Builder 对象
   */
  private void setStatementTimeout(Integer timeout, MappedStatement.Builder statementBuilder) {
    if (timeout == null) {
      timeout = configuration.getDefaultStatementTimeout();
    }
    statementBuilder.timeout(timeout);
  }

  /**
   * 构建结果映射
   *
   * @param resultType 结果类型
   * @param property 属性名
   * @param column 数据库列名
   * @param javaType Java类型
   * @param jdbcType JDBC类型
   * @param nestedSelect 嵌套查询ID
   * @param nestedResultMap 嵌套结果映射ID
   * @param notNullColumn 非空列名
   * @param columnPrefix 列名前缀
   * @param typeHandler 类型处理器
   * @param flags 结果标志列表
   * @param resultSet 结果集名称
   * @param foreignColumn 外键列名
   * @param lazy 是否懒加载
   * @return 构建的结果映射对象
   */
  public ResultMapping buildResultMapping(
      Class<?> resultType,
      String property,
      String column,
      Class<?> javaType,
      JdbcType jdbcType,
      String nestedSelect,
      String nestedResultMap,
      String notNullColumn,
      String columnPrefix,
      Class<? extends TypeHandler<?>> typeHandler,
      List<ResultFlag> flags,
      String resultSet,
      String foreignColumn, 
      boolean lazy) {
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    List<ResultMapping> composites = parseCompositeColumnName(column);
    if (composites.size() > 0) {
      column = null;
    }
    //构建result map
    ResultMapping.Builder builder = new ResultMapping.Builder(configuration, property, column, javaTypeClass);
    builder.jdbcType(jdbcType);
    builder.nestedQueryId(applyCurrentNamespace(nestedSelect, true));
    builder.nestedResultMapId(applyCurrentNamespace(nestedResultMap, true));
    builder.resultSet(resultSet);
    builder.typeHandler(typeHandlerInstance);
    builder.flags(flags == null ? new ArrayList<ResultFlag>() : flags);
    builder.composites(composites);
    builder.notNullColumns(parseMultipleColumnNames(notNullColumn));
    builder.columnPrefix(columnPrefix);
    builder.foreignColumn(foreignColumn);
    builder.lazy(lazy);
    return builder.build();
  }

  /**
   * 解析多个列名，将列名字符串分割成多个单独的列名并存储在集合中
   *
   * @param columnName 包含列名的字符串，列名之间可以用逗号、花括号或空格分隔
   * @return 包含单独列名的集合
   */
  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<String>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }

  /**
   * 解析复合列名，生成ResultMapping列表
   *
   * @param columnName 包含复合列信息的字符串
   * @return 包含解析后的ResultMapping对象的列表
   */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    List<ResultMapping> composites = new ArrayList<ResultMapping>();
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      while (parser.hasMoreTokens()) {
        String property = parser.nextToken();
        String column = parser.nextToken();
        ResultMapping.Builder complexBuilder = new ResultMapping.Builder(configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler());
        composites.add(complexBuilder.build());
      }
    }
    return composites;
  }

  private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
    if (javaType == null && property != null) {
      try {
        MetaClass metaResultType = MetaClass.forClass(resultType);
        javaType = metaResultType.getSetterType(property);
      } catch (Exception e) {}
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
    if (javaType == null) {
      if (JdbcType.CURSOR.equals(jdbcType)) {
        javaType = java.sql.ResultSet.class;
      } else if (Map.class.isAssignableFrom(resultType)) {
        javaType = Object.class;
      } else {
        MetaClass metaResultType = MetaClass.forClass(resultType);
        javaType = metaResultType.getGetterType(property);
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

  /** Backward compatibility signature */
  public ResultMapping buildResultMapping(
      Class<?> resultType,
      String property,
      String column,
      Class<?> javaType,
      JdbcType jdbcType,
      String nestedSelect,
      String nestedResultMap,
      String notNullColumn,
      String columnPrefix,
      Class<? extends TypeHandler<?>> typeHandler,
      List<ResultFlag> flags) {
      return buildResultMapping(
        resultType, property, column, javaType, jdbcType, nestedSelect, 
        nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
  }  

  public LanguageDriver getLanguageDriver(Class<?> langClass) {
    if (langClass != null) {
      configuration.getLanguageRegistry().register(langClass);
    } else {
      langClass = configuration.getLanguageRegistry().getDefaultDriverClass();
    }
    return configuration.getLanguageRegistry().getDriver(langClass);
  }

  /** Backward compatibility signature */
  //向后兼容方法
  public MappedStatement addMappedStatement(
    String id,
    SqlSource sqlSource,
    StatementType statementType,
    SqlCommandType sqlCommandType,
    Integer fetchSize,
    Integer timeout,
    String parameterMap,
    Class<?> parameterType,
    String resultMap,
    Class<?> resultType,
    ResultSetType resultSetType,
    boolean flushCache,
    boolean useCache,
    boolean resultOrdered,
    KeyGenerator keyGenerator,
    String keyProperty,
    String keyColumn,
    String databaseId,
    LanguageDriver lang) {
    return addMappedStatement(
      id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, 
      parameterMap, parameterType, resultMap, resultType, resultSetType, 
      flushCache, useCache, resultOrdered, keyGenerator, keyProperty, 
      keyColumn, databaseId, lang, null);
  }

}
