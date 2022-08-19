import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 获取国家统计局行政区划数据
 *
 * @author Waising Yung
 * {@code @date} 2021/11/2 10:16
 */
public class GetData {

    /**
     * 地址
     */
    private static final String SITE_URL = "http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2021/index.html";

    /**
     * 重试次数
     */
    private static final int RETRY_TIMES = 5;

    public static void main(String[] args) throws IOException {
        //线程池大小设为5，同时处理5个省的数据
        ExecutorService pool = new ThreadPoolExecutor(5, 5,
                1L, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(30),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

        Document doc = Jsoup.connect(SITE_URL).get();
        //提取名为provincetr的tr标签,获取里面全部的a标签链接
        Elements links = doc.select("tr.provincetr").select("a");
        for (Element e : links) {
            pool.submit(new GetProvince(e));
        }
        pool.shutdown();
    }

    /**
     * 获取省数据
     */
    static class GetProvince implements Runnable {

        private final Element ELE;

        public GetProvince(Element ele) {
            this.ELE = ele;
        }

        @Override
        public void run() {
            System.out.println("start to handle " + ELE.text() + " data, thread: " + Thread.currentThread().getName());
            DivisionEntity division = new DivisionEntity();
            //获取href标签地址
            String href = ELE.attr("href");
            //以.分割，取代码
            String[] arr = href.split("\\.");
            division.setProvinceCode(arr[0]);
            division.setProvinceName(ELE.text());
            //href的绝地路径
            String absHref = getAbsHref(ELE);
            getCity(absHref, division);
            //保存该省数据
            save(division);
        }

        /**
         * 获取市数据
         * <tr class='citytr'><td><a href='65/6501.html'>650100000000</a></td><td><a href='65/6501.html'>乌鲁木齐市</a></td></tr>
         *
         * @param url      链接
         * @param division 对象
         */
        private static void getCity(String url, DivisionEntity division) {
            try {
                Document doc = connect(url);
                Elements links = Objects.requireNonNull(doc).select("tr.citytr");
                for (Element e : links) {
                    DivisionEntity city = new DivisionEntity();
                    //获取全部a标签
                    Elements aList = e.select("a");
                    //第一个a标签是代码
                    Element codeEle = aList.get(0);
                    //第二个a标签是名称
                    Element nameEle = aList.get(1);
                    //只取前四位，后面的0去掉
                    city.setCityCode(codeEle.text().substring(0, 4));
                    String cityName = nameEle.text();
                    if ("市辖区".equals(cityName)) {
                        city.setCityName(division.getProvinceName());
                    } else {
                        city.setCityName(nameEle.text());
                    }
                    String absHref = getAbsHref(codeEle);
                    getCounty(absHref, city);
                    division.getSub().add(city);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 获取区县数据
         * <tr class='countytr'><td><a href='01/130102.html'>130102000000</a></td><td><a href='01/130102.html'>长安区</a></td></tr>
         *
         * @param url      链接
         * @param division 对象
         */
        private static void getCounty(String url, DivisionEntity division) {
            try {
                Document doc = connect(url);
                boolean flag = true;
                //广东省东莞市、中山市，海南省三沙市、儋州市、甘肃省嘉峪关市等五个城市，不设区县，市一级直接到乡镇一级
                //link:https://view.inews.qq.com/a/20210913A0AV3G00
                //这里使用市名作为区县名
                Elements links = Objects.requireNonNull(doc).select("tr.countytr");
                if (links.size() == 0) {
                    links = Objects.requireNonNull(doc).select("tr.towntr");
                    flag = false;
                }
                for (Element e : links) {
                    DivisionEntity county = new DivisionEntity();
                    Elements aList = e.select("a");
                    if (aList.size() > 0) {
                        county.setCountyCode(flag ? aList.get(0).text().substring(0, 6) : division.getCityCode());
                        county.setCountyName(flag ? aList.get(1).text() : division.getCityName());
                        String absHref;
                        if (flag) {
                            absHref = getAbsHref(aList.get(0));
                        } else {
                            absHref = doc.baseUri();
                        }
                        getTown(absHref, county);
                    } else {
                        aList = e.select("td");
                        county.setCountyCode(flag ? aList.get(0).text().substring(0, 6) : division.getCityCode());
                        county.setCountyName(flag ? aList.get(1).text() : division.getCityName());
                    }
                    division.getSub().add(county);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 获取乡镇数据
         * <tr class='towntr'><td><a href='07/110107001.html'>110107001000</a></td><td><a href='07/110107001.html'>八宝山街道办事处</a></td></tr>
         *
         * @param url      链接
         * @param division 对象
         */
        private static void getTown(String url, DivisionEntity division) {
            try {
                Document doc = connect(url);
                Elements links = Objects.requireNonNull(doc).select("tr.towntr");
                for (Element e : links) {
                    DivisionEntity town = new DivisionEntity();
                    Elements aList = e.select("a");
                    if (aList.size() > 0) {
                        town.setTownCode(aList.get(0).text().substring(0, 9));
                        town.setTownName(aList.get(1).text());
                        String absHref = getAbsHref(aList.get(0));
                        getVillage(absHref, town);
                    } else {
                        aList = e.select("td");
                        town.setTownCode(aList.get(0).text().substring(0, 9));
                        town.setTownName(aList.get(1).text());
                    }
                    division.getSub().add(town);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 获取乡镇数据
         * <tr class='villagetr'><td></td><td>420222104001</td><td>121</td><td>太子街社区委员会</td></tr>
         *
         * @param url      链接
         * @param division 对象
         */
        private static void getVillage(String url, DivisionEntity division) {
            try {
                Document doc = connect(url);
                Elements links = Objects.requireNonNull(doc).select("tr.villagetr");
                for (Element e : links) {
                    DivisionEntity village = new DivisionEntity();
                    Elements aList = e.select("td");
                    village.setVillageCode(aList.get(0).text());
                    village.setRegionType(aList.get(1).text());
                    village.setVillageName(aList.get(2).text());
                    division.getSub().add(village);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 获取href的绝地路径
         *
         * @param e 元素
         * @return 绝对路径
         */
        private static String getAbsHref(Element e) {
            return e.attr("abs:href");
        }

        /**
         * 超时重试
         */
        private static Document connect(String url) {
            int i = 0;
            //超时重试
            while (i < RETRY_TIMES) {
                try {
                    Thread.sleep(1000);
                    //打开网页，获取网页元素
                    //超时时间5秒
                    return Jsoup.connect(url).get();
                } catch (Exception e) {
                    ++i;
                    System.out.println(url + " retry times:" + i);
                }
            }
            return null;
        }

        /**
         * 保存到数据库
         *
         * @param division 对象
         */
        private static void save(DivisionEntity division) {
            //驱动名
            String driver = "com.mysql.cj.jdbc.Driver";

            //数据库地址
            //地址必须加上“rewriteBatchedStatements=true”，不然没法批量插入
            String url = "jdbc:mysql://localhost:3306/database?autoReconnect=true&useUnicode=true&" +
                    "characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&rewriteBatchedStatements=true";

            //用户名
            String username = "";

            //密码
            String password = "";

            Connection connection = null;
            PreparedStatement statement = null;

            try {
                Class.forName(driver);
                connection = DriverManager.getConnection(url, username, password);
                connection.setAutoCommit(false);
                statement = connection.prepareStatement("INSERT INTO all_division (division_id,province_name," +
                        "province_code,city_name,city_code,county_name,county_code,town_name,town_code,village_name," +
                        "village_code,address_name,region_type,active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                System.out.println("start to insert " + division.getProvinceName() + " data");
                for (DivisionEntity city : division.getSub()) {
                    String provinceName = division.getProvinceName();
                    String provinceCode = division.getProvinceCode();
                    String cityName = city.getCityName();
                    String cityCode = city.getCityCode();
                    for (DivisionEntity county : city.getSub()) {
                        String countyName = county.getCountyName();
                        String countyCode = county.getCountyCode();
                        for (DivisionEntity town : county.getSub()) {
                            String townName = town.getTownName();
                            String townCode = town.getTownCode();
                            for (DivisionEntity village : town.getSub()) {
                                statement.setString(1, village.getVillageCode());
                                statement.setString(2, provinceName);
                                statement.setString(3, provinceCode);
                                statement.setString(4, cityName);
                                statement.setString(5, cityCode);
                                statement.setString(6, countyName);
                                statement.setString(7, countyCode);
                                statement.setString(8, townName);
                                statement.setString(9, townCode);
                                statement.setString(10, village.getVillageName());
                                statement.setString(11, village.getVillageCode());
                                statement.setString(12, provinceName + cityName + countyName + townName + provinceName);
                                statement.setString(13, village.getRegionType());
                                statement.setInt(14, 1);
                                statement.addBatch();
                            }
                        }
                    }
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
