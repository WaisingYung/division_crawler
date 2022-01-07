# Division crawler

爬取国家统计局行政区划（省市区县乡镇村），五级数据



## 内容列表

* 描述
* 安装
* 使用
* 许可证

## 描述

一个用于爬取[国家统计局行政区划](http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2020/)数据的小工具，爬取的数据存入到MySQL数据库中。数据库表如下：

```mysql
+---------------+--------------+------+-----+---------+-------+
| Field         | Type         | Null | Key | Default | Extra |
+---------------+--------------+------+-----+---------+-------+
| division_id   | varchar(64)  | NO   | PRI | NULL    |       |
| province_name | varchar(16)  | YES  |     | NULL    |       |
| province_code | varchar(16)  | NO   |     | NULL    |       |
| city_name     | varchar(16)  | YES  |     | NULL    |       |
| city_code     | varchar(16)  | NO   |     | NULL    |       |
| county_name   | varchar(16)  | YES  |     | NULL    |       |
| county_code   | varchar(16)  | NO   |     | NULL    |       |
| town_name     | varchar(32)  | YES  |     | NULL    |       |
| town_code     | varchar(16)  | NO   |     | NULL    |       |
| village_name  | varchar(32)  | YES  |     | NULL    |       |
| village_code  | varchar(16)  | NO   |     | NULL    |       |
| address_name  | varchar(255) | YES  |     | NULL    |       |
| region_type   | varchar(8)   | YES  |     | NULL    |       |
| active        | int(2)       | YES  |     | NULL    |       |
+---------------+--------------+------+-----+---------+-------+
```

示例数据：

```
110101010026	北京市	11	北京市	1101	东城区	110101	和平里街道	110101010	青年湖社区居委会	110101010026	北京市北京市东城区和平里街道北京市	111	1
```



## 安装

本项目使用到 Jdk1.8.x 及 Maven 3.8.x，请确保你使用的环境已安装

```shell
$ mvn install
```

## 运行
```shell
$ nohup java -jar division_crawler-1.1.0.RELEASE.jar >> log.log 2>&1 &
```

## 使用

* 创建数据库表

工具爬取的数据会保存至数据库中，默认建表命令如下如下：

```mysql
CREATE TABLE `all_division` (
  `division_id` varchar(20) NOT NULL COMMENT '编码',
  `province_name` varchar(16) DEFAULT NULL COMMENT '省份名称',
  `province_code` varchar(16) NOT NULL COMMENT '省份编码',
  `city_name` varchar(16) DEFAULT NULL COMMENT '城市名称',
  `city_code` varchar(16) NOT NULL COMMENT '城市编码',
  `county_name` varchar(16) DEFAULT NULL COMMENT '区县名称',
  `county_code` varchar(16) NOT NULL COMMENT '区县编码',
  `town_name` varchar(32) DEFAULT NULL COMMENT '乡镇名称',
  `town_code` varchar(16) NOT NULL COMMENT '乡镇编码',
  `village_name` varchar(32) DEFAULT NULL COMMENT '村（社区）名称',
  `village_code` varchar(16) NOT NULL COMMENT '村（社区）编码',
  `address_name` varchar(255) DEFAULT NULL COMMENT '完整地址',
  `region_type` varchar(8) DEFAULT NULL COMMENT '识别码',
  `active` int(2) DEFAULT NULL COMMENT '1可用，0不可用',
  PRIMARY KEY (`division_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='全国行政区划数据';
```

* 更改爬取年份

工具默认爬取2020年数据，如需更改年份，请修改：

```java
private static final String SITE_URL = ""
```

* 更改重试次数

爬虫可能应各种情况导致连接失败或超时，故需要重新连接，默认重连次数为5次，如需更改，请修改：

```java
private static final int RETRY_TIMES = 5;
```

* 配置数据库

数据库配置在GetData的save方法中

```java
//驱动名
String driver = "com.mysql.cj.jdbc.Driver"
    
//数据库地址
String url = "jdbc:mysql://localhost:3306/database?autoReconnect=true&useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&rewriteBatchedStatements=true";

//用户名
String username = "";

//密码
String password = "";
```



## 许可证

[MIT](https://github.com/RichardLitt/standard-readme/blob/master/LICENSE) © Richard Littauer

 ![license](https://img.shields.io/badge/license-MIT-green)