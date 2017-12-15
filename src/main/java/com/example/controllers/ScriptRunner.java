package com.example.controllers;

import com.eclipsesource.v8.*;
import com.eclipsesource.v8.utils.V8Executor;
import com.example.annotations.JSComponent;
import com.example.annotations.JSRunnable;
import com.example.components.RedisOps;
import com.example.components.TimeOps;
import com.example.datatypes.Converter;
import com.example.datatypes.Dummy;
import org.apache.log4j.Logger;
import org.reflections.Reflections;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

@RestController
public class ScriptRunner {

    private static ScriptRunner runner = new ScriptRunner();

    private static Logger log;

    private static V8 v8;

    private ScriptRunner() {
        log = Logger.getLogger(ScriptRunner.class.getName());
        v8 = V8.createV8Runtime();

        v8.registerV8Executor(new V8Object(v8), new V8Executor(""));

        v8.executeScript("converter = {}");
        v8.executeScript("dummy = {}");

        this.Initializer("com.example.components");
        this.Initializer("com.example.controllers");
        this.register(java.lang.String.class, "string", false);
        this.registerStatic(java.lang.Math.class, "math", false);
        this.register(redis.clients.jedis.Jedis.class, RedisOps.getJedis(), "redis", false);
//        this.register(com.example.components.RedisOps.class, "redis", false);

        v8.add("dir", System.getProperty("user.dir")+"\\src\\main\\resources\\");

    }

    public ScriptRunner getScriptRunner() {return runner;}

    public void Initializer(String packageName) {
        Reflections classes = new Reflections(packageName);
        Set<Class<?>> annotated = classes.getTypesAnnotatedWith(JSComponent.class);

        for(Class c : annotated) {
            String name = ((JSComponent)c.getAnnotation(JSComponent.class)).name();
            if(name.equals("[unassigned]")){register(c);}else{register(c, name);};
        }
    }

    public void register(Class c) {register(c, c.getSimpleName().toLowerCase());}

    public void register(Class c, String name) {register(c,name,true);}

    private void register(Class c, String name, boolean annotationNeaded) {register(c,null,name,annotationNeaded);}

    private void register(Class c, Object o, String name, boolean annotationNeaded) {
        V8Object obj = new V8Object(v8);
        v8.add(name, obj);
        log.debug(c.getName() + " start registration as: " + name);

        try {
            if(o == null)
                o = c.newInstance();
        } catch(IllegalAccessException e) {
            try {
                for (Constructor con : c.getConstructors()) {
                    con.setAccessible(true);
                    con.newInstance(null);
                }
            } catch (InstantiationException e2) {
                e2.printStackTrace();
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
            } catch (InvocationTargetException e2) {
                e2.printStackTrace();
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        for (Method m : c.getDeclaredMethods()) {
            if(m.isAnnotationPresent(JSRunnable.class) || !annotationNeaded) {
                try{
                    m.setAccessible(true);
                    if(m.getReturnType() == Long.class) {
                        Converter<Long> con = new Converter<>(o, m.getName());
                        obj.registerJavaMethod(con, "run", m.getName(), con.getClass().getDeclaredMethod("run").getParameterTypes());
                        v8.executeScript(name + "." + m.getName() + " = converter.run");
//                        v8.executeScript("converter.toString = null");
                    } else {
                        obj.registerJavaMethod(o, m.getName(), m.getName(), m.getParameterTypes());
                    }
                    log.debug("method: \"" + m.getName() + "\" \n\twith parameters: (" + m.getParameterTypes() + ")\n\thas been registered for object: " + name);
                } catch(RuntimeException e) { System.err.println(e.toString()+">"+e.getCause()+":"+e.getMessage()+":"+m.getName());
                } catch(NoSuchMethodException e) { System.err.println(e.toString()+">"+e.getCause()+":"+e.getMessage()+":"+m.getName());}
            }
        }

        for (Field f : c.getDeclaredFields()) {
            if(f.isAnnotationPresent(JSComponent.class) || !annotationNeaded) {
                f.setAccessible(true);
                Class c2 = f.getType();
                Object val = new Object();
                try {
                    val = f.get(o);
                } catch(IllegalAccessException e) {
                    e.printStackTrace();
                }
                if(c2 == String.class)
                    obj.add(f.getName(), val.toString());
                else if(c2.isPrimitive() && val instanceof Number) {
                    if(val instanceof Double)
                        obj.add(f.getName(), (Double) val);
                    else if(val instanceof Integer)
                        obj.add(f.getName(), (Integer) val);
                } else if(c2.isPrimitive() && val instanceof Boolean)
                    obj.add(f.getName(), (Boolean)val);
                log.debug("field: \"" + f.getName() + "\"\n\tWith a value of: " + val + "\n\thas been registered for object: " + name);
            }
        }
        obj.release();
        log.debug(c.getName() + " has been registered as: " + name);
    }

    private void registerStatic(Class c, String name, boolean annotationNeaded) {
        V8Object obj = new V8Object(v8);
        v8.add(name, obj);
        log.debug(c.getName() + " start registration as: " + name);

//        Object o = null
//
//        try {
//            if(o == null)
//                o = c.newInstance();
//        } catch(IllegalAccessException e) {
//            try {
//                for (Constructor con : c.getConstructors()) {
//                    con.setAccessible(true);
//                    o = con.newInstance(null);
//                }
//            } catch (InstantiationException e2) {
//                e2.printStackTrace();
//            } catch (IllegalAccessException e2) {
//                e2.printStackTrace();
//            } catch (InvocationTargetException e2) {
//                e2.printStackTrace();
//            }
//        } catch (InstantiationException e) {
//            e.printStackTrace();
//        }

        for (Method m : c.getDeclaredMethods()) {
            if(m.isAnnotationPresent(JSRunnable.class) || !annotationNeaded) {
                try{
                    m.setAccessible(true);
                    Dummy d = new Dummy(c, m.getName());
                    obj.registerJavaMethod(d, "run", m.getName(), new Class[] {Object[].class});//d.getClass().getDeclaredMethod("run").getParameterTypes());
                    System.out.println("function " + name + "." + m.getName() + "(a) { dummy.run(a);}");
                    v8.executeScript(name + "." + m.getName() +  " = function dostuff(a) { dummy.run(a);}");
//                    v8.executeScript("converter.toString = null");
                    log.debug("method: \"" + m.getName() + "\" \n\twith parameters: (" + m.getParameterTypes() + ")\n\thas been registered for object: " + name);
                } catch(RuntimeException e) { System.err.println(e.toString()+">"+e.getCause()+":"+e.getMessage()+":"+m.getName());
                } //catch(NoSuchMethodException e) { System.err.println(e.toString()+">"+e.getCause()+":"+e.getMessage()+":"+m.getName());}
            }
        }

        for (Field f : c.getDeclaredFields()) {
            if(f.isAnnotationPresent(JSComponent.class) || !annotationNeaded) {
                f.setAccessible(true);
                Class c2 = f.getType();
                Object val = new Object();
                try {
                    val = f.get(null);
                } catch(IllegalAccessException e) {
                    e.printStackTrace();
                }
                if(c2 == String.class)
                    obj.add(f.getName(), val.toString());
                else if(c2.isPrimitive() && val instanceof Number) {
                    if(val instanceof Double)
                        obj.add(f.getName(), (Double) val);
                    else if(val instanceof Integer)
                        obj.add(f.getName(), (Integer) val);
                } else if(c2.isPrimitive() && val instanceof Boolean)
                    obj.add(f.getName(), (Boolean)val);
                log.debug("field: \"" + f.getName() + "\"\n\tWith a value of: " + val + "\n\thas been registered for object: " + name);
            }
        }
        obj.release();
        log.debug(c.getName() + " has been registered as: " + name);
    }

    @RequestMapping(path="/run_script/{script}")
    public static void runScript(@PathVariable String s) {
        runScriptWithReturn(s);
    }

    @RequestMapping(path="/run_script_with_return/{script}")
    public static synchronized String runScriptWithReturn(@PathVariable String s) {return runScriptWithReturn(s,true);}

    @RequestMapping(path="/run_script_with_return/{script}")
    public static synchronized String runScriptWithReturn(@PathVariable String s, boolean needsRelease) {
        V8Locker locker = v8.getLocker();
        String ret = "";

        boolean flag = true;
        while(flag) {
            try {

                locker.acquire();
                ret += v8.executeScript(s);
                if(needsRelease)
                    locker.release();

                flag = false;

            } catch(Error e) {}
        }

        return ret;
    }

    public static void main(String args[]) {CLI.run();}
}
