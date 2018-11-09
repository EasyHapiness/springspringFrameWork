package com.gupaoedu.mvcframework.servlet;

import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapping;
import com.gupaoedu.mvcframework.annotation.GPService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class GPDispatcherServlet extends HttpServlet {


    private static  final String LOCATION = "contextConfigLocation";

    private Properties p = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String, Object>();

    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    public GPDispatcherServlet(){
        super();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        this.doGet(req, resp);
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDispatcher(HttpServletRequest req,HttpServletResponse resp) throws Exception{

        if (handlerMapping.isEmpty()){return;}

        String url = req.getRequestURI();
        String context = req.getContextPath();
        url = url.replace(context,"").replaceAll("/+","/");

        if (!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 not found!");
            return;
        }

        Map<String,String[]> params = req.getParameterMap();
        Method method = this.handlerMapping.get(url);

        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();

        //获取请求参数
        Map<String,String[]> parameterMap = req.getParameterMap();

        //保存参数值
        Object[] paramvalues = new Object[parameterTypes.length];
        for (int i=0;i<parameterTypes.length;i++){

            Class parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class){

                paramvalues[i] = req;
                continue;
            }else if (parameterType == HttpServletResponse.class){
                paramvalues[i] = resp;
            }else if (parameterType == String.class){
                for (Map.Entry<String,String[]> entry : parameterMap.entrySet()){
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]]","").replaceAll(",\\s",",");
                    paramvalues[i] = value;
                }
            }

        }

        try {

            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());

            //利用发射来调用
            method.invoke(this.ioc.get(beanName),paramvalues);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2.扫描相关的包文件
        scanPackage(p.getProperty("scanPackage"));

        //3.初始化相关示例，并保存到IOC
        doInstance();

        //4.依赖出入
        doAutowired();

        //5.构造handlerMapping
        initHandlerMapping();

        this.init(config);
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry entry : ioc.entrySet()){
            Class<?> calzz = entry.getValue().getClass();
            if (!calzz.isAnnotationPresent(GPController.class)){
                continue;
            }
            String baseUrl = "";
            if (calzz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping gpRequestMapping = calzz.getAnnotation(GPRequestMapping.class);
                baseUrl = gpRequestMapping.value();
            }

            //获取method的url
            Method[] method = calzz.getMethods();
            for (Method method1 : method){

                if (!method1.isAnnotationPresent(GPRequestMapping.class)){
                    continue;
                }

                GPRequestMapping  gp = method1.getAnnotation(GPRequestMapping.class);
                String url = ("/" + baseUrl + "/" + gp.value()).replaceAll("/+","/");
                handlerMapping.put(url,method1);

                System.out.println("mapper " + url +"," +method1);
            }
        }
    }

    private void doAutowired() {

        if (ioc.isEmpty()){return;}
        for (Map.Entry entry : ioc.entrySet()){
            Field[]  fields = entry.getValue().getClass().getDeclaredFields();

            for (Field field : fields){
                if (!field.isAnnotationPresent(GPAutowired.class)){
                    continue;
                }
                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);
                String beanName = gpAutowired.value().trim();
                if ("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void doInstance() {

        if (classNames.size() ==0 ){return;}
        try{
            for (String className : classNames){
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)){
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                } else if (clazz.isAnnotationPresent(GPService.class)){

                    GPService gPService = clazz.getAnnotation(GPService.class);
                    String beanName = gPService.value();

                    //如果用户自己设置了名字，就用用户自己设置的
                    if (!"".equals(beanName.trim())){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }

                    //如果自己设置，就实例化这个示例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else
                    continue;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public String lowerFirstCase(String string){

        char[] chara = string.toCharArray();
        chara[0] += 32;
        return String.valueOf(chara);
    }

    private void scanPackage(String packageName) {

        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()){
            if (file.isDirectory()){
                scanPackage(packageName +"." + file.getName());
            }else {
                classNames.add(packageName+"."+ file.getName().replace(".class","").trim());
            }
        }
    }

    private void doLoadConfig(String location) {

        InputStream fis = null;
        try{
            fis = this.getClass().getClassLoader().getResourceAsStream(location);

            //读取配置文件
            p.load(fis);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if (fis != null){
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
