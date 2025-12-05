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
package org.apache.ibatis.builder.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.CacheNamespaceRef;
import org.apache.ibatis.annotations.Case;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Lang;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.TypeDiscriminator;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.FetchType;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * @author Clinton Begin
 */
/**
 * 注解方式构建mapper
 * 解析注解，将注解信息转换为MyBatis内部的核心数据结构（MappeedStatement，ResultMap等）
 * 最终注册到Configuration中，为后续的SQL执行提供支持
 */
public class MapperAnnotationBuilder {

  /**
   * 存储SQL注解类型（@Select/@Insert/@Update/@Delete）
   */
  private final Set<Class<? extends Annotation>> sqlAnnotationTypes = new HashSet<Class<? extends Annotation>>();
  /**
   * 存储SQL提供者注解类型（@SelectProvider 等，用于动态生成 SQL）
   */
  private final Set<Class<? extends Annotation>> sqlProviderAnnotationTypes = new HashSet<Class<? extends Annotation>>();

  /**
   * 全局配置
   */
  private Configuration configuration;
  /**
   * Mapper构建助手，封装了创建ResultMap，MappedStatement等的工具方法
   */
  private MapperBuilderAssistant assistant;
  /**
   * 当前要解析的Mapper接口类型（UserMapper.class）
   */
  private Class<?> type;

  public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
    // 生成当前mapper的资源标识（用于标识是否已经加载）
    String resource = type.getName().replace('.', '/') + ".java (best guess)";
    // 初始化构建助手
    this.assistant = new MapperBuilderAssistant(configuration, resource);
    this.configuration = configuration;
    this.type = type;

    // 初始化基础SQL注解集合
    sqlAnnotationTypes.add(Select.class);
    sqlAnnotationTypes.add(Insert.class);
    sqlAnnotationTypes.add(Update.class);
    sqlAnnotationTypes.add(Delete.class);

    // 初始化基础SQL提供者注解集合
    sqlProviderAnnotationTypes.add(SelectProvider.class);
    sqlProviderAnnotationTypes.add(InsertProvider.class);
    sqlProviderAnnotationTypes.add(UpdateProvider.class);
    sqlProviderAnnotationTypes.add(DeleteProvider.class);
  }

  /**
   * 入口方法，解析Mapper接口的注解
   */
  public void parse() {
    // 基于mapper文件名，避免重复解析mapper
    String resource = type.toString();
    if (!configuration.isResourceLoaded(resource)) {
      // 加载对应的xml资源，如果存在，注解和xml可混合使用
      loadXmlResource();
      // 标记当前资源已经加载
      configuration.addLoadedResource(resource);
      // 设置当前命名空间为Mapper接口的全限定名
      assistant.setCurrentNamespace(type.getName());
        // 解析缓存相关注解@CacheNamespace
      parseCache();
      // 解析缓存引用注解@CacheNamespaceRef（复用其它Mapper的缓存）
      parseCacheRef();
      // 遍历所有方法
      Method[] methods = type.getMethods();
      for (Method method : methods) {
        try {
          // 跳过桥接方法（泛型擦除产生的方法）
          if (!method.isBridge()) {
            // 解析单个方法的注解
            parseStatement(method);
          }
        } catch (IncompleteElementException e) {
          // 如果解析不完整(缺少资源)，加入待解析集合，稍后重新解析
          configuration.addIncompleteMethod(new MethodResolver(this, method));
        }
      }
    }
    // 解析之前暂存的不完整方法
    parsePendingMethods();
  }

  /**
   * 解析之前的不完整方法
   */
  private void parsePendingMethods() {
    // 从全局配置中获取待解析方法集合
    Collection<MethodResolver> incompleteMethods = configuration.getIncompleteMethods();
    // 加锁处理，避免多线程情况下同时修改
    synchronized (incompleteMethods) {
      // 获取迭代器，处理并移除未处理的方法
      Iterator<MethodResolver> iter = incompleteMethods.iterator();
      while (iter.hasNext()) {
        try {
          // 解析并移除
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // This method is still missing a resource
        }
      }
    }
  }

  private void loadXmlResource() {
    if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
      // 构建xml资源路径
      String xmlResource = type.getName().replace('.', '/') + ".xml";
      InputStream inputStream = null;
      try {
        inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
      } catch (IOException e) {
        // ignore, resource is not required
      }
      if (inputStream != null) {
        // 解析xml资源
        XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
        xmlParser.parse();
      }
    }
  }

  /**
   * 解析缓存注解@CacheNamespace
   */
  private void parseCache() {
    CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
    if (cacheDomain != null) {
      Integer size = cacheDomain.size() == 0 ? null : cacheDomain.size();
      Long flushInterval = cacheDomain.flushInterval() == 0 ? null : cacheDomain.flushInterval();
      assistant.useNewCache(cacheDomain.implementation(), cacheDomain.eviction(), flushInterval, size, cacheDomain.readWrite(), cacheDomain.blocking(), null);
    }
  }

  /**
   * 解析缓存引用注解@CacheNamespaceRef
   */
  private void parseCacheRef() {
    CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
    if (cacheDomainRef != null) {
      assistant.useCacheRef(cacheDomainRef.value().getName());
    }
  }

  /**
   * 解析结果映射
   * @param method
   * @return
   */
  private String parseResultMap(Method method) {
    // 获取方法的返回类型
    Class<?> returnType = getReturnType(method);
    // 解析@ConstructorArgs、@Results、@TypeDiscriminator注解
    ConstructorArgs args = method.getAnnotation(ConstructorArgs.class);
    Results results = method.getAnnotation(Results.class);
    TypeDiscriminator typeDiscriminator = method.getAnnotation(TypeDiscriminator.class);
    // 生成唯一的ResultMap ID
    String resultMapId = generateResultMapName(method);
    // 应用映射规则，创建ResultMap并注册到Configuration
    applyResultMap(resultMapId, returnType, argsIf(args), resultsIf(results), typeDiscriminator);
    return resultMapId;
  }

  /**
   * 生成ResultMap ID
   * @param method
   * @return
   */
  private String generateResultMapName(Method method) {
    StringBuilder suffix = new StringBuilder();
    for (Class<?> c : method.getParameterTypes()) {
      suffix.append("-");
      suffix.append(c.getSimpleName());
    }
    if (suffix.length() < 1) {
      suffix.append("-void");
    }
    return type.getName() + "." + method.getName() + suffix;
  }

  /**
   * 应用结果映射规则，创建ResultMap
   * @param resultMapId
   * @param returnType
   * @param args
   * @param results
   * @param discriminator
   */
  private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results, TypeDiscriminator discriminator) {
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    applyConstructorArgs(args, returnType, resultMappings);
    applyResults(results, returnType, resultMappings);
    Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);
    assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);
    createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
  }

  /**
   * 创建基于鉴别器的ResultMap
   * @param resultMapId
   * @param resultType
   * @param discriminator
   */
  private void createDiscriminatorResultMaps(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      for (Case c : discriminator.cases()) {
        String caseResultMapId = resultMapId + "-" + c.value();
        List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
        applyConstructorArgs(c.constructArgs(), resultType, resultMappings);
        applyResults(c.results(), resultType, resultMappings);
        assistant.addResultMap(caseResultMapId, c.type(), resultMapId, null, resultMappings, null);
      }
    }
  }

  /**
   * 应用构造函数参数映射
   * @param resultMapId
   * @param resultType
   * @param discriminator
   * @return
   */
  private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      String column = discriminator.column();
      Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
      JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
      Class<? extends TypeHandler<?>> typeHandler = discriminator.typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler();
      Case[] cases = discriminator.cases();
      Map<String, String> discriminatorMap = new HashMap<String, String>();
      for (Case c : cases) {
        String value = c.value();
        String caseResultMapId = resultMapId + "-" + value;
        discriminatorMap.put(value, caseResultMapId);
      }
      return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
    }
    return null;
  }

  /**
   * 最核心的方法，将单个Mapper方法的注解解析为MapperStatement
   * 负责将单个 Mapper 方法的注解（如`@Select("SELECT * FROM user WHERE id=#{id}")`）解析为`MappedStatement`（MyBatis 执行 SQL 的核心对象）
   * @param method
   */
  void parseStatement(Method method) {
    // 获取方法的参数类型，用于参数绑定
    Class<?> parameterTypeClass = getParameterType(method);
    // 获取语言驱动，默认是XMl语言驱动，支持${}和#{}占位符解析
    LanguageDriver languageDriver = getLanguageDriver(method);
    // 从方法注解中获取SQL语句源
    // SqlSource中封装了SQL语句信息和参数信息
    SqlSource sqlSource = getSqlSourceFromAnnotations(method, parameterTypeClass, languageDriver);
    // 只有解析了SQL源才继续
    if (sqlSource != null) {
      // 解析@Options注解（配置查询超时，缓存策略等）
      Options options = method.getAnnotation(Options.class);
        // 生成MappedStatement的唯一标识，格式为：接口全限定名.方法名
      final String mappedStatementId = type.getName() + "." + method.getName();
      // 初始化默认配置（查询超时，Statement类型，缓存策略等）
      Integer fetchSize = null;
      Integer timeout = null;
      // 默认预处理语句
      StatementType statementType = StatementType.PREPARED;
      ResultSetType resultSetType = ResultSetType.FORWARD_ONLY;
      // 确定SQL命令类型（SELECT/INSERT/UPDATE/DELETE）
      SqlCommandType sqlCommandType = getSqlCommandType(method);
      boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
      // SELECT不刷新缓存
      boolean flushCache = !isSelect;
      // SELECT启用缓存
      boolean useCache = isSelect;

      // 处理主键生成器
      KeyGenerator keyGenerator;
      // 默认主键属性名
      String keyProperty = "id";
      String keyColumn = null;
      if (SqlCommandType.INSERT.equals(sqlCommandType) || SqlCommandType.UPDATE.equals(sqlCommandType)) {
        // first check for SelectKey annotation - that overrides everything else
        SelectKey selectKey = method.getAnnotation(SelectKey.class);
        if (selectKey != null) {
          // 解析@SelectKey注解（自定义主键查询逻辑）
          keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method), languageDriver);
          keyProperty = selectKey.keyProperty();
        } else if (options == null) {
          // 使用全局配置的主键生成策略
          keyGenerator = configuration.isUseGeneratedKeys() ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
        } else {
            // 最后都没有才使用解析@Options注解中的主键生成策略
          keyGenerator = options.useGeneratedKeys() ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
          keyProperty = options.keyProperty();
          keyColumn = options.keyColumn();
        }
      } else {
        // 否则无主键生成策略
        keyGenerator = new NoKeyGenerator();
      }
      // 覆盖默认配置
      if (options != null) {
        flushCache = options.flushCache();
        useCache = options.useCache();
        fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null; //issue #348
        timeout = options.timeout() > -1 ? options.timeout() : null;
        statementType = options.statementType();
        resultSetType = options.resultSetType();
      }
      // 解析结果映射（ResultMap）
      // 处理数据库字段与Java实体的映射
      String resultMapId = null;
      ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
      if (resultMapAnnotation != null) {
        String[] resultMaps = resultMapAnnotation.value();
        // 如果有多个ResultMap，拼接它们的ID
        StringBuilder sb = new StringBuilder();
        for (String resultMap : resultMaps) {
          if (sb.length() > 0) {
            sb.append(",");
          }
          sb.append(resultMap);
        }
        resultMapId = sb.toString();
      } else if (isSelect) {
        // 自动生成ResultMap
        resultMapId = parseResultMap(method);
      }

      // 通过助手类构建MappedStatement并注册到Configuration
      assistant.addMappedStatement(
          mappedStatementId, // SQL映射ID
          sqlSource, // SQL源
          statementType, // 语句类型
          sqlCommandType, // SQL命令类型
          fetchSize, // fetchSize
          timeout, // timeout
          null, // ParameterMap，已废弃，用ParameterType代替
          parameterTypeClass, // 参数类型
          resultMapId, // 结果映射ID
          getReturnType(method), // 结果类型
          resultSetType,// 结果集类型
          flushCache, // 是否刷新缓存
          useCache, // 是否使用缓存
          false, // 是否为嵌套结果映射
          keyGenerator, // 主键生成器
          keyProperty, // 主键属性
          keyColumn, // 主键列
          null, // 数据库ID（多数据源时用）
          languageDriver, // 语言驱动
          null); // 结果集名称（多结果集时用）
    }
  }

  /**
   * 解析@Lang注解，获取语言驱动
   * @param method
   * @return
   */
  private LanguageDriver getLanguageDriver(Method method) {
    Lang lang = method.getAnnotation(Lang.class);
    Class<?> langClass = null;
    if (lang != null) {
      langClass = lang.value();
    }
    return assistant.getLanguageDriver(langClass);
  }

  /**
   * 解析方法参数
   * @param method
   * @return
   */
  private Class<?> getParameterType(Method method) {
    Class<?> parameterType = null;
    Class<?>[] parameterTypes = method.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!RowBounds.class.isAssignableFrom(parameterTypes[i]) && !ResultHandler.class.isAssignableFrom(parameterTypes[i])) {
        if (parameterType == null) {
          parameterType = parameterTypes[i];
        } else {
          // issue #135
          parameterType = ParamMap.class;
        }
      }
    }
    return parameterType;
  }

  /**
   * 获得返回值类型
   * @param method
   * @return
   */
  private Class<?> getReturnType(Method method) {
    Class<?> returnType = method.getReturnType();
    if (void.class.equals(returnType)) {
      // void类型，从@ResultType注解中获取
      // 为什么返回void还要获取返回值类型？
      // 这里的ResultType其实是SQL执行后的返回值类型，而不是方法的返回值类型
      // 告知结果映射目标
      ResultType rt = method.getAnnotation(ResultType.class);
      if (rt != null) {
        returnType = rt.value();
      } 
    } else if (Collection.class.isAssignableFrom(returnType)) {
      // 集合类型，获取泛型参数类型
      Type returnTypeParameter = method.getGenericReturnType();
      if (returnTypeParameter instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnTypeParameter).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnTypeParameter = actualTypeArguments[0];
          if (returnTypeParameter instanceof Class) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          } else if (returnTypeParameter instanceof GenericArrayType) {
            Class<?> componentType = (Class<?>) ((GenericArrayType) returnTypeParameter).getGenericComponentType();
            returnType = Array.newInstance(componentType, 0).getClass();
          }
        }
      }
    } else if (method.isAnnotationPresent(MapKey.class) && Map.class.isAssignableFrom(returnType)) {
      // Map类型，获取泛型参数的value类型
      Type returnTypeParameter = method.getGenericReturnType();
      if (returnTypeParameter instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnTypeParameter).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 2) {
          returnTypeParameter = actualTypeArguments[1];
          if (returnTypeParameter instanceof Class) {
            returnType = (Class<?>) returnTypeParameter;
          } else if (returnTypeParameter instanceof ParameterizedType) {
            // (issue 443) actual type can be a also a parameterized type
            returnType = (Class<?>) ((ParameterizedType) returnTypeParameter).getRawType();
          }
        }
      }
    }
    // 否则就是实体类类型，直接返回
    return returnType;
  }

  /**
   * 提取SQL源
   * @param method
   * @param parameterType
   * @param languageDriver
   * @return
   */
  private SqlSource getSqlSourceFromAnnotations(Method method, Class<?> parameterType, LanguageDriver languageDriver) {
    try {
      // 获取SQL注解类型和SQL提供者注解类型
      Class<? extends Annotation> sqlAnnotationType = getSqlAnnotationType(method);
      Class<? extends Annotation> sqlProviderAnnotationType = getSqlProviderAnnotationType(method);
      // 只能存在一个，不允许同时使用
      if (sqlAnnotationType != null) {
        if (sqlProviderAnnotationType != null) {
          throw new BindingException("You cannot supply both a static SQL and SqlProvider to method named " + method.getName());
        }
        // 获取SQL注解实例，也就是@Select注解中的SQL语句
        Annotation sqlAnnotation = method.getAnnotation(sqlAnnotationType);
        final String[] strings = (String[]) sqlAnnotation.getClass().getMethod("value").invoke(sqlAnnotation);
        // 构建SQL源，处理SQL中的参数占位符
        return buildSqlSourceFromStrings(strings, parameterType, languageDriver);
      } else if (sqlProviderAnnotationType != null) {
        Annotation sqlProviderAnnotation = method.getAnnotation(sqlProviderAnnotationType);
        // 处理动态SQL注解
        return new ProviderSqlSource(assistant.getConfiguration(), sqlProviderAnnotation);
      }
      return null;
    } catch (Exception e) {
      throw new BuilderException("Could not find value method on SQL annotation.  Cause: " + e, e);
    }
  }

  private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    final StringBuilder sql = new StringBuilder();
    for (String fragment : strings) {
      sql.append(fragment);
      sql.append(" ");
    }
    return languageDriver.createSqlSource(configuration, sql.toString(), parameterTypeClass);
  }

  private SqlCommandType getSqlCommandType(Method method) {
    Class<? extends Annotation> type = getSqlAnnotationType(method);

    if (type == null) {
      type = getSqlProviderAnnotationType(method);

      if (type == null) {
        return SqlCommandType.UNKNOWN;
      }

      if (type == SelectProvider.class) {
        type = Select.class;
      } else if (type == InsertProvider.class) {
        type = Insert.class;
      } else if (type == UpdateProvider.class) {
        type = Update.class;
      } else if (type == DeleteProvider.class) {
        type = Delete.class;
      }
    }

    return SqlCommandType.valueOf(type.getSimpleName().toUpperCase(Locale.ENGLISH));
  }

  private Class<? extends Annotation> getSqlAnnotationType(Method method) {
    return chooseAnnotationType(method, sqlAnnotationTypes);
  }

  private Class<? extends Annotation> getSqlProviderAnnotationType(Method method) {
    return chooseAnnotationType(method, sqlProviderAnnotationTypes);
  }

  private Class<? extends Annotation> chooseAnnotationType(Method method, Set<Class<? extends Annotation>> types) {
    for (Class<? extends Annotation> type : types) {
      Annotation annotation = method.getAnnotation(type);
      if (annotation != null) {
        return type;
      }
    }
    return null;
  }

  private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Result result : results) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      if (result.id()) {
        flags.add(ResultFlag.ID);
      }
      ResultMapping resultMapping = assistant.buildResultMapping(
          resultType,
          nullOrEmpty(result.property()),
          nullOrEmpty(result.column()),
          result.javaType() == void.class ? null : result.javaType(),
          result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
          hasNestedSelect(result) ? nestedSelectId(result) : null,
          null,
          null,
          null,
          result.typeHandler() == UnknownTypeHandler.class ? null : result.typeHandler(),
          flags,
          null,
          null,
          isLazy(result));
      resultMappings.add(resultMapping);
    }
  }

  private String nestedSelectId(Result result) {
    String nestedSelect = result.one().select();
    if (nestedSelect.length() < 1) {
      nestedSelect = result.many().select();
    }
    if (!nestedSelect.contains(".")) {
      nestedSelect = type.getName() + "." + nestedSelect;
    }
    return nestedSelect;
  }

  private boolean isLazy(Result result) {
    boolean isLazy = configuration.isLazyLoadingEnabled();
    if (result.one().select().length() > 0 && FetchType.DEFAULT != result.one().fetchType()) {
      isLazy = (result.one().fetchType() == FetchType.LAZY);
    } else if (result.many().select().length() > 0 && FetchType.DEFAULT != result.many().fetchType()) {
      isLazy = (result.many().fetchType() == FetchType.LAZY);
    }
    return isLazy;
  }
  
  private boolean hasNestedSelect(Result result) {
    if (result.one().select().length() > 0 && result.many().select().length() > 0) {
      throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
    }
    return result.one().select().length() > 0 || result.many().select().length() > 0;  
  }

  private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Arg arg : args) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if (arg.id()) {
        flags.add(ResultFlag.ID);
      }
      ResultMapping resultMapping = assistant.buildResultMapping(
          resultType,
          null,
          nullOrEmpty(arg.column()),
          arg.javaType() == void.class ? null : arg.javaType(),
          arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(),
          nullOrEmpty(arg.select()),
          nullOrEmpty(arg.resultMap()),
          null,
          null,
          arg.typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler(),
          flags,
          null,
          null,
          false);
      resultMappings.add(resultMapping);
    }
  }

  private String nullOrEmpty(String value) {
    return value == null || value.trim().length() == 0 ? null : value;
  }

  private Result[] resultsIf(Results results) {
    return results == null ? new Result[0] : results.value();
  }

  private Arg[] argsIf(ConstructorArgs args) {
    return args == null ? new Arg[0] : args.value();
  }

  private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    Class<?> resultTypeClass = selectKeyAnnotation.resultType();
    StatementType statementType = selectKeyAnnotation.statementType();
    String keyProperty = selectKeyAnnotation.keyProperty();
    String keyColumn = selectKeyAnnotation.keyColumn();
    boolean executeBefore = selectKeyAnnotation.before();

    // defaults
    boolean useCache = false;
    KeyGenerator keyGenerator = new NoKeyGenerator();
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    SqlSource sqlSource = buildSqlSourceFromStrings(selectKeyAnnotation.statement(), parameterTypeClass, languageDriver);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
        flushCache, useCache, false,
        keyGenerator, keyProperty, keyColumn, null, languageDriver, null);

    id = assistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
    configuration.addKeyGenerator(id, answer);
    return answer;
  }

}
