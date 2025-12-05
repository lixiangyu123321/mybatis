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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
/**
 * XML配置构建器，建造者模式,继承BaseBuilder
 * MyBatis解析XML配置文件的核心入口，将XML配置转化为MyBatis可运行的Configuration对象
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 标记配置是否已解析，避免重复解析
   */
  private boolean parsed;
  /**
   * XPath解析器
   * 用于解析XML节点
   */
  private XPathParser parser;
  /**
   * 指定的环境 development/production
   */
  private String environment;

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  /**
   * 初始化解析器与配置
   * 接受不同的输入源（Reader/InputStream），创建XPathParser
   * 初始化Configuration，设置全局变量Properties
   * @param parser
   * @param environment
   * @param props
   */
  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // 初始化父类（BaseBuilder）的Configuration对象
    super(new Configuration());
    // 错误上下文，记录当前解析的资源
    ErrorContext.instance().resource("SQL Mapper Configuration");
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 对外暴露的解析入口
   * @return
   */
  public Configuration parse() {
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;
    // 解析全局配置的根节点
    // 理论上来讲有两种根节点
    // 1. 是全局配置的根节点configuration
    // 2. 是XMLMapper的根节点mapper
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 用于解析全局配置的
   * @param root
   */
  private void parseConfiguration(XNode root) {
    try {
      // 解析<properties>
      propertiesElement(root.evalNode("properties"));
      // 解析<typeAliases>
      typeAliasesElement(root.evalNode("typeAliases"));
      // 解析<plugins>
      pluginElement(root.evalNode("plugins"));
      // 解析<objectFactory>
      objectFactoryElement(root.evalNode("objectFactory"));
      // 以此类推
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      settingsElement(root.evalNode("settings"));
      environmentsElement(root.evalNode("environments"));
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      typeHandlerElement(root.evalNode("typeHandlers"));
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 遍历 <typeAliases> 节点下的所有子节点（<package> 或 <typeAlias>）；
   * 根据子节点类型，要么批量注册指定包下所有类的别名，要么手动注册单个类的别名；
   * 将注册好的别名存入 TypeAliasRegistry（MyBatis 内置的别名注册表），供后续解析 SQL 映射文件时使用。
   * @param parent 对应XML文件的<typeAliases>节点
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 遍历子节点
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          /**
           * 这是批量注册别名的逻辑（对应配置：<package name="com.example.entity"/>）：
           * child.getName()：获取当前子节点的名称（这里是 package）；
           * child.getStringAttribute("name")：读取 <package> 节点的 name 属性值（即要扫描的包名，如 com.example.entity）；
           * configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage)：调用 TypeAliasRegistry 的批量注册方法，扫描指定包下的所有类，为每个类自动生成别名：
           * 默认别名规则：类名首字母小写（如 com.example.entity.User → user）；
           * 若类上有 @Alias 注解（如 @Alias("myUser")），则优先使用注解指定的别名。
           */
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          /**
           * 这是手动注册单个别名的逻辑（对应配置：<typeAlias alias="User" type="com.example.entity.User"/>）：
           * else：当前子节点是 <typeAlias>（而非 <package>）；
           * child.getStringAttribute("alias")：读取 <typeAlias> 的 alias 属性（自定义别名，如 User）；
           * child.getStringAttribute("type")：读取 <typeAlias> 的 type 属性（要注册别名的类的全类名，如 com.example.entity.User）。
           */
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              // 未手动指定别名，类名作为别名
              typeAliasRegistry.registerAlias(clazz);
            } else {
              // 手动指定别名
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 遍历 <plugins> 节点下的所有 <plugin> 子节点；
   * 解析每个 <plugin> 节点的 interceptor 属性（插件全类名）和内部的 <property> 配置；
   * 通过反射创建插件实例，设置配置属性；
   * 将插件实例注册到 Configuration 的 InterceptorChain（拦截器链）中，供 MyBatis 运行时调用。
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    // 避免空指针
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        /**
         * child.getChildrenAsProperties()：将 <plugin> 节点下的所有 <property> 子节点转换为 Properties 对象
         * <plugin interceptor="org.mybatis.example.ExamplePlugin">
         *   <property name="pageSize" value="10"/>
         *   <property name="dialect" value="mysql"/>
         * </plugin>
         */
        Properties properties = child.getChildrenAsProperties();
        /**
         * 这行是反射创建插件实例的核心，拆解为两步：
         * resolveClass(interceptor)：MyBatis 封装的类加载方法（继承自 BaseBuilder），根据全类名获取对应的 Class 对象（如 ExamplePlugin.class）；
         * .newInstance()：通过反射创建类的实例（要求插件类有无参构造方法）；
         * 强制类型转换为 Interceptor：MyBatis 所有插件必须实现 org.apache.ibatis.plugin.Interceptor 接口，否则会抛出类型转换异常。
         */
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        //将拦截器加入到configuration的拦截器链中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }


  /**
   * 判断配置文件中是否存在 <objectFactory> 节点；
   * 解析节点的 type 属性（指定对象工厂的全类名）和内部的 <property> 配置；
   * 通过反射创建 ObjectFactory 实例，设置自定义属性；
   * 将实例绑定到 Configuration 中，替代 MyBatis 默认的 DefaultObjectFactory。
   * <objectFactory type="org.mybatis.example.MyObjectFactory">
   *   <property name="maxSize" value="100"/>
   *   <property name="lazyInit" value="true"/>
   * </objectFactory>
   * 简单说：MyBatis 不会直接用 new User() 创建对象，而是通过 ObjectFactory 的 create() 方法来创建 ——
   * 这是 MyBatis 提供的扩展点，默认实现足够满足日常需求，但也支持自定义扩展。
   * 支持在创建对象是加入自定义逻辑（比如日志记录、对象初始化、属性注入、对象池复用）
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      // 依旧反射创建对象工厂
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 判断配置文件中是否存在 <objectWrapperFactory> 节点；
   * 解析节点的 type 属性（指定对象包装工厂的全类名）；
   * 通过反射创建 ObjectWrapperFactory 实例；
   * 将实例绑定到 Configuration 中，替代 MyBatis 默认的 DefaultObjectWrapperFactory。
   * ObjectWrapper：对象包装器，封装了对不同类型对象的属性读写逻辑，MyBatis 内置了 3 种核心实现：
   * BeanWrapper：处理 POJO 对象（如 User），通过 getter/setter 访问属性；
   * MapWrapper：处理 Map 对象，通过 put/get 访问键值；
   * CollectionWrapper：处理集合对象（如 List/Set），提供集合操作的统一接口；
   * ObjectWrapperFactory：对象包装工厂，核心职责是 “根据对象类型创建对应的 ObjectWrapper”
   *
   * ObjectWrapper 是 MyBatis 操作所有对象的 “统一适配器”
   * 为什么需要 ObjectWrapper？
   * 没有 ObjectWrapper 的话，MyBatis 操作不同对象会面临两个问题：
   * 操作逻辑不统一：
   * 操作 POJO（如 User）：需要调用 getUsername()/setUsername()；
   * 操作 Map：需要调用 get("username")/put("username", "张三")；
   * 操作集合（如 List）：需要调用 get(0)/add()；
   * MyBatis 无法用一套逻辑处理所有对象。
   * 底层细节暴露：MyBatis 作为框架，需要屏蔽不同对象的访问细节，让上层逻辑（如结果映射、参数绑定）更简洁。
   * @param context
   * @throws Exception
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   * 解析 <properties> 节点内直接定义的 <property> 子节点，存入 Properties 对象；
   * 加载外部属性文件（通过 resource 类路径或 url 绝对路径），并合并到上述 Properties；
   * 合并已有的全局变量，最终将所有属性设置到 XPathParser 和 Configuration 中，供后续解析配置时替换占位符（如 ${username}、${url}）。
   * <properties>
   *   <property name="username" value="dev_user"/>
   *   <property name="password" value="123456"/>
   * </properties>
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      /**
       * context.getChildrenAsProperties()：
       * 将 <properties> 下的所有 <property name="key" value="value"/> 子节点转换为 Properties 对象；
       */
      Properties defaults = context.getChildrenAsProperties();
      /**
       * context.getStringAttribute("resource")：读取 <properties> 节点的 resource 属性（类路径下的属性文件，如 resource="db.properties"）；
       * context.getStringAttribute("url")：读取 <properties> 节点的 url 属性（绝对路径的属性文件，如 url="file:///var/config/db.properties"）；
       */
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // 任一生效
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }


  /**
   * 是 XMLConfigBuilder 中专门解析 MyBatis 配置文件里 <settings> 节点的核心逻辑。
   * <settings> 是 MyBatis 最核心的全局配置节点，用于调整框架运行时的核心行为（如缓存、懒加载、驼峰命名转换等），
   * 而这个方法的作用就是将配置的 setting 项映射到 Configuration 对象的对应属性中，
   * 是 MyBatis 初始化核心参数的关键步骤。
   * 解析 <settings> 节点下的所有 <setting> 子节点，转换为 Properties 对象；
   * 校验配置的 setting 项是否合法（是否对应 Configuration 的 setter 方法）；
   * 将每个合法的 setting 项转换为对应类型（布尔、枚举、类实例等），并设置到 Configuration 对象中；
   * 为未配置的 setting 项设置合理的默认值，保证框架正常运行。
   * @param context
   * @throws Exception
   */
  private void settingsElement(XNode context) throws Exception {
    if (context != null) {
      Properties props = context.getChildrenAsProperties();
      MetaClass metaConfig = MetaClass.forClass(Configuration.class);
      /**
       * MetaClass metaConfig = MetaClass.forClass(Configuration.class)：MetaClass 是 MyBatis 反射工具类，用于封装 Configuration 类的元信息（如 setter/getter 方法）；
       * 遍历 props 的所有 key（即配置的 setting 项名称，如 mapUnderscoreToCamelCase）；
       * metaConfig.hasSetter(String.valueOf(key))：检查 Configuration 类是否有对应的 setter 方法（如 setMapUnderscoreToCamelCase(boolean)）；
       * 若不存在对应的 setter 方法，抛出 BuilderException，提示 “配置项不存在，检查拼写（区分大小写）”—— 这是 MyBatis 防止配置写错的关键校验（比如把 mapUnderscoreToCamelCase 写成 mapunderscoretocamelcase 会直接报错）。
       */
      for (Object key : props.keySet()) {
        if (!metaConfig.hasSetter(String.valueOf(key))) {
          throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
        }
      }
      //AutoMappingBehavior.valueOf(...)：将字符串转换为枚举类型（AutoMappingBehavior 是 MyBatis 枚举，取值：NONE/PARTIAL/FULL）；
      // 设置自动映射行为（如 PARTIAL 表示只自动映射非嵌套的结果集）。
      configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
      // 设置全局缓存开关（默认开启）。
      configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
      /**
       * 解析 proxyFactory 配置项：
       * props.getProperty("proxyFactory")：获取自定义代理工厂的全类名（如 org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory）；
       * createInstance(...)：通过反射创建代理工厂实例（内部调用 resolveClass().newInstance()）；
       * 强制转换为 ProxyFactory 接口并设置 —— 用于创建懒加载的代理对象（默认使用 Javassist 或 JDK 动态代理）。
       */
      configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
      // lazyLoadingEnabled：全局懒加载开关（默认关闭）；
      configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
      // aggressiveLazyLoading：激进式懒加载（默认开启，即访问对象任意属性都会加载所有懒加载属性）
      configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), true));
      // multipleResultSetsEnabled：是否允许单个语句返回多个结果集（默认开启）；
      configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
      // useColumnLabel：使用列标签（Column Label）而非列名（Column Name）映射结果（默认开启）；
      configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
      // useGeneratedKeys：是否允许 JDBC 自动生成主键（默认关闭）。
      configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
      /**
       * ExecutorType 是枚举（SIMPLE/REUSE/BATCH），默认 SIMPLE（简单执行器）；
       * 设置默认的执行器类型（如 BATCH 用于批量更新 / 插入优化）。
       */
      configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
      /**
       * integerValueOf(...)：将字符串转为整数，未配置则为 null；
       * 设置 SQL 语句的默认超时时间（单位：秒，默认无超时）。
       */
      configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
      /**
       * 解析 mapUnderscoreToCamelCase 配置项：
       * 核心配置：是否开启驼峰命名自动转换（如数据库 user_name 映射到 POJO userName）；
       * 默认关闭，配置为 true 后无需手动写 resultMap 映射驼峰字段。
       */
      configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
      /**
       * 解析缓存 / 分页 / JDBC 类型相关配置：
       * safeRowBoundsEnabled：是否安全使用 RowBounds 分页（默认关闭）；
       * localCacheScope：本地缓存作用域（默认 SESSION，即会话级缓存；可选 STATEMENT，语句级缓存）；
       * jdbcTypeForNull：NULL 值对应的 JDBC 类型（默认 OTHER）。
       */
      configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
      configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
      configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
      /**
       * 解析 lazyLoadTriggerMethods 配置项：
       * stringSetValueOf(..., "equals,clone,hashCode,toString")：将逗号分隔的字符串转为 Set<String>，未配置则使用默认值；
       * 设置触发懒加载的方法（默认调用这些方法时，会强制加载懒加载属性）。
       */
      configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
      /**
       * 剩余配置项解析：
       * safeResultHandlerEnabled：是否安全使用 ResultHandler（默认开启）；
       * defaultScriptingLanguage：默认脚本语言（如 XML 动态 SQL 的解析器，默认 MVEL）；
       * callSettersOnNulls：是否为 NULL 值调用 setter 方法（默认关闭）；
       * logPrefix：日志前缀；
       * logImpl：日志实现类（如 SLF4J/LOG4J）；
       * configurationFactory：自定义 Configuration 工厂类。
       */
      configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
      configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
      configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
      configuration.setLogPrefix(props.getProperty("logPrefix"));
      configuration.setLogImpl(resolveClass(props.getProperty("logImpl")));
      configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
    }
  }

  /**
   * 该方法的核心职责：
   * 读取 <environments> 节点的 default 属性，确定默认使用的环境 ID（如 development）；
   * 遍历 <environments> 下的所有 <environment> 子节点；
   * 匹配当前环境 ID 与默认环境 ID，仅初始化匹配的环境（避免加载所有环境的资源）；
   * 解析该环境下的 <transactionManager>（事务管理器）和 <dataSource>（数据源）；
   * 构建 Environment 对象并设置到 Configuration 中，供后续创建 SqlSessionFactory 时使用。
   *
   * <configuration>
   *   <!-- 多环境配置：default 指定默认环境为 development -->
   *   <environments default="development">
   *     <!-- 开发环境 -->
   *     <environment id="development">
   *       <transactionManager type="JDBC"/>
   *       <dataSource type="POOLED">
   *         <property name="driver" value="${jdbc.driver}"/>
   *         <property name="url" value="${jdbc.dev.url}"/>
   *         <property name="username" value="${jdbc.dev.username}"/>
   *         <property name="password" value="${jdbc.dev.password}"/>
   *       </dataSource>
   *     </environment>
   *
   *     <!-- 生产环境 -->
   *     <environment id="production">
   *       <transactionManager type="JDBC"/>
   *       <dataSource type="POOLED">
   *         <property name="driver" value="${jdbc.driver}"/>
   *         <property name="url" value="${jdbc.prod.url}"/>
   *         <property name="username" value="${jdbc.prod.username}"/>
   *         <property name="password" value="${jdbc.prod.password}"/>
   *       </dataSource>
   *     </environment>
   *   </environments>
   * </configuration>
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          /**
           * child.evalNode("transactionManager")：获取当前 <environment> 节点下的 <transactionManager> 子节点；
           * transactionManagerElement(...)：解析事务管理器节点，返回 TransactionFactory（事务工厂）实例
           */
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          /**
           * child.evalNode("dataSource")：获取当前 <environment> 节点下的 <dataSource> 子节点；
           * dataSourceElement(...)：解析数据源节点，返回 DataSourceFactory（数据源工厂）实例；
           */
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * DatabaseIdProvider 是 MyBatis 实现 “多数据库适配” 的底层识别工具——
   * 它的核心作用是识别当前连接的数据库类型并生成唯一标识（databaseId），
   * 而 “基于不同数据库执行不同 SQL” 是这个标识的最终应用场景
   *
   *
   * 解析 <databaseIdProvider> 节点，创建对应的 DatabaseIdProvider 实例；
   * 兼容老版本配置（将 VENDOR 映射为 DB_VENDOR）；
   * 给 DatabaseIdProvider 设置自定义属性（如数据库别名）；
   * 根据当前环境的数据源，识别数据库类型并生成 databaseId（如 mysql/oracle）；
   * 将 databaseId 设置到 Configuration 中，供后续解析 Mapper XML 时匹配数据库专属 SQL。
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      //与老版本兼容
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      /**
       * <databaseIdProvider type="DB_VENDOR">
       *   <property name="MySQL" value="mysql"/>
       *   <property name="Oracle" value="oracle"/>
       *   <property name="PostgreSQL" value="pg"/>
       * </databaseIdProvider>
       * 执行后 properties 中会包含 {MySQL=mysql, Oracle=oracle, PostgreSQL=pg}，作用是给不同数据库厂商名称设置自定义别名。
       */
      Properties properties = context.getChildrenAsProperties();
      /**
       * resolveClass(type).newInstance()：通过反射创建 DatabaseIdProvider 实例（type=DB_VENDOR 时，创建 VendorDatabaseIdProvider）；
       * databaseIdProvider.setProperties(properties)：将自定义别名属性设置到实例中；
       * VendorDatabaseIdProvider 的核心逻辑：读取数据库元数据中的 “数据库厂商名称”，匹配 properties 中的映射关系，生成最终的 databaseId。
       */
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  /**
   * 该方法的作用是根据配置的事务管理器类型（如 JDBC/MANAGED），
   * 通过反射创建对应的 TransactionFactory（事务工厂）实例，
   * 并设置自定义属性，最终返回该工厂供环境初始化使用。
   *
   * 解析 <transactionManager> 节点的 type 属性，确定事务管理器类型；
   * 读取节点下的 <property> 子节点，转换为配置属性；
   * 通过反射创建 TransactionFactory 接口的实现类实例；
   * 为事务工厂设置自定义属性并返回；
   * 若节点为空，抛出异常（事务工厂是环境配置的必填项）。
   * @param context
   * @return
   * @throws Exception
   */
  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  /**
   * <dataSource> 是 MyBatis 配置数据库连接的核心节点，
   * 该方法的作用是根据配置的数据源类型（如 POOLED/UNPOOLED/JNDI），
   * 通过反射创建对应的 DataSourceFactory（数据源工厂）实例，
   * 设置数据库连接参数（如 URL、用户名、密码），最终返回该工厂供环境初始化时创建 DataSource 实例
   *
   * 解析 <dataSource> 节点的 type 属性，确定数据源工厂类型；
   * 读取节点下的 <property> 子节点（如 driver、url、username 等），转换为配置属性；
   * 通过反射创建 DataSourceFactory 接口的实现类实例；
   * 为数据源工厂设置数据库连接参数并返回；
   * 若节点为空，抛出异常（数据源工厂是环境配置的必填项）。
   *
   * <dataSource type="POOLED">
   *   <property name="driver" value="com.mysql.cj.jdbc.Driver"/>
   *   <property name="url" value="jdbc:mysql://localhost:3306/mybatis_demo"/>
   *   <property name="username" value="root"/>
   *   <property name="password" value="123456"/>
   *   <property name="poolMaximumActiveConnections" value="20"/>
   * </dataSource>
   * @param context
   * @return
   * @throws Exception
   */
  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      /**
       * 类型取值规则：
       * 内置类型：可直接写简写（POOLED/UNPOOLED/JNDI），MyBatis 会自动映射到对应的实现类：
       * POOLED → org.apache.ibatis.datasource.pooled.PooledDataSourceFactory（带连接池的数据源工厂）；
       * UNPOOLED → org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory（无连接池的数据源工厂）；
       * JNDI → org.apache.ibatis.datasource.jndi.JndiDataSourceFactory（JNDI 数据源工厂）；
       */
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      // 基于反射根据类型创建不同的数据源工厂
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }


  /**
   * TypeHandler（类型处理器）是 MyBatis 实现 Java 类型与 JDBC 类型双向转换的核心组件，
   * 该方法的作用是解析 <typeHandlers> 下的配置（批量注册包下的处理器 / 单独注册指定处理器），
   * 并将这些类型处理器注册到 TypeHandlerRegistry（类型处理器注册表）中，
   * 让 MyBatis 能在 SQL 执行时自动匹配并使用对应的处理器。
   *
   * 遍历 <typeHandlers> 节点下的所有子节点，区分两种配置方式：
   * 批量注册：通过 <package> 节点指定包名，自动扫描并注册该包下所有 TypeHandler 实现类；
   * 单独注册：通过 <typeHandler> 节点指定 Java 类型、JDBC 类型和处理器类，精准注册指定处理器；
   * 解析配置参数（Java 类型、JDBC 类型、处理器类），转换为对应 Class / 枚举类型；
   * 将解析后的类型处理器注册到 TypeHandlerRegistry 中，供 MyBatis 运行时使用。
   * @param parent
   * @throws Exception
   */
  private void typeHandlerElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          /**
           * 批量注册逻辑：匹配子节点名为 package 的情况（对应 <package name="com.mybatis.typehandler"/>）；
           * child.getStringAttribute("name")：读取 <package> 节点的 name 属性，即要扫描的包名；
           * typeHandlerRegistry.register(typeHandlerPackage)：调用注册表的包扫描方法，自动扫描该包下所有实现 TypeHandler 接口的类，并注册到注册表中；
           * 核心逻辑：MyBatis 会遍历包下的所有类，判断是否实现 TypeHandler，若是则根据类上的注解（如 @MappedJdbcTypes/@MappedTypes）自动关联 Java 类型和 JDBC 类型。
           *
           * <typeHandlers>
           *   <!-- 扫描 com.mybatis.typehandler 包下所有 TypeHandler -->
           *   <package name="com.mybatis.typehandler"/>
           * </typeHandlers>
           * 要求：包下的处理器类需实现 TypeHandler<T> 接口，或继承 BaseTypeHandler<T>；
           */
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          /**
           * 单独注册逻辑：子节点非 package 时，判定为 <typeHandler> 节点
           * （对应 <typeHandler javaType="String" jdbcType="VARCHAR" handler="com.mybatis.handler.MyStringTypeHandler"/>）。
           */
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              // 没有jdbcType， 按照类型处理器的逻辑来
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              // “Java 类型 + JDBC 类型 → 处理器” 的精准映射
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 是 XMLConfigBuilder 中专门解析 MyBatis 配置文件里 <mappers> 节点的核心逻辑。
   * <mappers> 是 MyBatis 加载 Mapper 映射器（XML 文件 / 接口类）的核心配置节点，
   * 该方法的作用是解析 <mappers> 下的配置（批量扫描包 / 指定资源路径 / 指定 URL / 指定接口类），
   * 将 Mapper 注册到 Configuration 中，让 MyBatis 能加载 SQL 映射语句、绑定 Mapper 接口与 XML 文件。
   *
   * 遍历 <mappers> 节点下的所有子节点，支持四种 Mapper 加载方式：
   * 批量扫描：通过 <package> 节点指定包名，自动扫描并注册该包下所有 Mapper 接口 + 对应的 XML 文件；
   * 类路径加载：通过 <mapper resource="xxx/xxx/Mapper.xml"/> 加载 XML 映射文件；
   * URL 加载：通过 <mapper url="file:///xxx/Mapper.xml"/> 加载绝对路径的 XML 映射文件；
   * 接口类加载：通过 <mapper class="com.xxx.MapperInterface"/> 加载注解式 Mapper 接口；
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          /**
           * 批量扫描包（推荐方式）：匹配子节点名为 package 的情况（对应 <package name="com.mybatis.mapper"/>）；
           * child.getStringAttribute("name")：读取 <package> 节点的 name 属性，即要扫描的 Mapper 包名；
           * configuration.addMappers(mapperPackage)：核心逻辑是扫描该包下所有 Mapper 接口：
           * 若接口有对应的 XML 文件（同名同路径），自动加载 XML 并绑定接口；
           * 若接口是注解式（如 @Select），直接注册接口中的 SQL 语句；
           * 有xml文件加载xml文件，有mapper接口加载mapper接口
           *
           * <mappers>
           *   <package name="com.mybatis.mapper"/>
           * </mappers>
           */
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // 单独加载 Mapper
          /**
           * 读取 <mapper> 节点的三个互斥属性：
           * resource：类路径下的 XML 文件路径（如 mapper/UserMapper.xml）；
           * url：绝对路径的 XML 文件 URL（如 file:///D:/project/mapper/UserMapper.xml）；
           * class：Mapper 接口的全类名（如 com.mybatis.mapper.UserMapper）。
           *
           * <mappers>
           *   <mapper resource="mapper/UserMapper.xml"/>
           * </mappers>
           */
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            /**
             * 类路径加载 XML 文件：仅配置 resource 时触发；
             * ErrorContext.instance().resource(resource)：设置错误上下文（便于后续报错时定位到具体的 Mapper 文件）；
             * Resources.getResourceAsStream(resource)：从类路径加载 XML 文件，获取输入流；
             * new XMLMapperBuilder(...)：创建 XML 映射器解析器，参数包括：输入流、全局配置、当前 Mapper 资源名、SQL 片段缓存；
             * mapperParser.parse()：解析 XML 文件，将 <select>/<insert> 等 SQL 语句注册到 Configuration 中，并绑定 Mapper 接口；
             * 关键：每个 Mapper 都新建 XMLMapperBuilder，避免解析状态冲突。
             *
             * <mappers>
             *   <mapper url="file:///D:/project/mybatis/mapper/UserMapper.xml"/>
             * </mappers>
             */
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            /**
             * URL 加载 XML 文件：仅配置 url 时触发；
             * 逻辑与 resource 方式一致，区别仅在于：
             * Resources.getUrlAsStream(url)：从绝对 URL 加载 XML 文件（适合非类路径下的文件）；
             * 错误上下文绑定 url，便于定位问题。
             *
             * <mappers>
             *   <mapper class="com.mybatis.mapper.UserMapper"/>
             * </mappers>
             */
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            /**
             * 加载Mapper接口类
             */
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  /**
   * 检验当前默认环境是否已经配置
   * 检验当前环境节点的ID是否存在
   * 对比默认环境ID和当前环境ID，返回是否匹配的布尔值
   * @param id
   * @return
   */
  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
