package org.apache.dubbo.maven;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DubboPurifier {

    private final File output;

    private final Set<String> basePackages;

    private final Map<Class<?>, File> classMap = new HashMap<>();

    protected DubboPurifier(File output, Set<String> basePackages) {
        this.output = output;
        this.basePackages = basePackages;
    }

    public void purify(Class<?> interfaceClass) throws Exception {
        if (interfaceClass.isInterface()) {
            decompile(interfaceClass);
        }
    }

    private void decompile(Class<?> clazz) throws Exception {
        if (isIgnored(clazz)) {
            return;
        }
        File javaFile = new File(this.output, clazz.getName().replace('.', File.separatorChar).concat(".java"));
        javaFile.getParentFile().mkdirs();
        this.classMap.put(clazz, javaFile);
        if (clazz.isInterface()) { //interface
            decompileInterface(clazz, javaFile);
        } else if (clazz.isEnum()) { //enum
            decompileEnum(clazz, javaFile);
        } else { //pojo
            decompilePojo(clazz, javaFile);
        }
    }

    private void decompile(ParameterizedType parameterizedType) throws Exception {
        this.decompile(parameterizedType.getRawType());
        for (Type type : parameterizedType.getActualTypeArguments()) {
            this.decompile(type);
        }
    }

    private void decompile(Type type) throws Exception {
        if (type instanceof Class) {
            decompile((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            decompile((ParameterizedType) type);
        }
    }

    private void decompileInterface(Class<?> clazz, File javaFile) throws Exception {
        if (!clazz.isInterface()) {
            return;
        }
        if (javaFile.exists()) {
            javaFile.delete();
        }
        try (FileWriter fw = new FileWriter(javaFile)) {
            BufferedWriter writer = new BufferedWriter(fw);
            doWritePackageName(writer, clazz);
            doWriteBegin4Interface(writer, clazz);
            doWriteContent4Interface(writer, clazz);
            doWriteEnd(writer);
            writer.flush();
        }
    }

    private void decompileEnum(Class<?> clazz, File javaFile) throws Exception {
        if (!clazz.isEnum()) {
            return;
        }
        if (javaFile.exists()) {
            javaFile.delete();
        }
        try (FileWriter fw = new FileWriter(javaFile)) {
            BufferedWriter writer = new BufferedWriter(fw);
            doWritePackageName(writer, clazz);
            writer.newLine();
            writer.append("@lombok.Getter");
            writer.newLine();
            writer.append("@lombok.AllArgsConstructor");
            doWriteBegin4Enum(writer, clazz);
            doWriteContent4Enum(writer, clazz);
            doWriteEnd(writer);
            writer.flush();
        }
    }

    private void decompilePojo(Class<?> clazz, File javaFile) throws Exception {
        if (javaFile.exists()) {
            javaFile.delete();
        }
        try (FileWriter fw = new FileWriter(javaFile)) {
            BufferedWriter writer = new BufferedWriter(fw);
            doWritePackageName(writer, clazz);
            writer.newLine();
            writer.append("@lombok.Data");
            doWriteBegin4Pojo(writer, clazz);
            doWriteContent4Pojo(writer, clazz);
            doWriteEnd(writer);
            writer.flush();
        }
    }

    private void doWritePackageName(BufferedWriter writer, Class<?> clazz) throws Exception {
        Package pkg = clazz.getPackage();
        if (pkg != null) {
            writer.append("package ").append(pkg.getName()).append(";");
        }
    }

    private void doWriteBegin4Pojo(BufferedWriter writer, Class<?> clazz) throws Exception {
        writer.newLine();
        writer.append(clazz.toGenericString().replace(clazz.getName(), clazz.getSimpleName()));
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            writer.append(" extends ").append(superclass.getName());
            decompile(superclass);
        }
        Class<?>[] interfaceClasses = clazz.getInterfaces();
        if (interfaceClasses != null && interfaceClasses.length > 0) {
            for (Class<?> interfaceClass : interfaceClasses) {
                if (java.io.Serializable.class.isAssignableFrom(interfaceClass)) {
                    writer.append(" implements java.io.Serializable");
                    break;
                }
            }
        }
        writer.append(" {");
    }

    private void doWriteBegin4Interface(BufferedWriter writer, Class<?> clazz) throws Exception {
        writer.newLine();
        writer.append(clazz.toGenericString().replace(clazz.getName(), clazz.getSimpleName()));
        this.appendInterfaces(writer, clazz);
        writer.append(" {");
    }

    private void doWriteBegin4Enum(BufferedWriter writer, Class<?> clazz) throws Exception {
        writer.newLine();
        writer.append(clazz.toGenericString().replace("final", "").replace(clazz.getName(), clazz.getSimpleName()));
        this.appendInterfaces(writer, clazz);
        writer.append(" {");
    }

    private void appendInterfaces(BufferedWriter writer, Class<?> clazz) throws Exception {
        Class<?>[] interfaceClasses = clazz.getInterfaces();
        if (interfaceClasses == null || interfaceClasses.length == 0) {
            return;
        }
        writer.append(" implements ");
        for (int i = 0; i < interfaceClasses.length; i++) {
            if (i > 0) {
                writer.append(", ");
            }
            writer.append(interfaceClasses[i].getName());
        }
    }

    private void doWriteEnd(BufferedWriter writer) throws Exception {
        writer.newLine();
        writer.append("}");
    }


    private void doWriteContent4Interface(BufferedWriter writer, Class<?> clazz) throws Exception {
        for (Method method : clazz.getDeclaredMethods()) {
            Type returnType = method.getGenericReturnType();
            //返回类型写入java文件
            this.decompile(returnType);

            writer.newLine();
            writer.append(returnType.getTypeName()).append(" ").append(method.getName());
            this.appendParameterTypes(writer, method);
            this.appendExceptionTypes(writer, method);
            writer.append(";");
        }
    }

    private void appendParameterTypes(BufferedWriter writer, Method method) throws Exception {
        writer.append("(");
        if (method.getParameterCount() > 0) {
            Type[] parameterTypes = method.getGenericParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    writer.append(", ");
                }
                Type parameterType = parameterTypes[i];
                writer.append(parameterType.getTypeName()).append(" var" + i);
                //参数类型写入java文件
                this.decompile(parameterType);
            }
        }
        writer.append(")");
    }

    private void appendExceptionTypes(BufferedWriter writer, Method method) throws Exception {
        Type[] exceptionTypes = method.getGenericExceptionTypes();
        if (exceptionTypes == null || exceptionTypes.length == 0) {
            return;
        }
        writer.append(" throws ");
        for (int i = 0; i < exceptionTypes.length; i++) {
            if (i > 0) {
                writer.append(", ");
            }
            writer.append(exceptionTypes[i].getTypeName());
            //异常类型写入java文件
            //TODO: writeToFile(parameterType);
        }
    }


    private void doWriteContent4Enum(BufferedWriter writer, Class<?> clazz) throws Exception {
        Object[] enums = (Object[]) clazz.getDeclaredMethod("values").invoke(clazz);
        if (enums == null || enums.length == 0) {
            return;
        }
        List<Field> fields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        Method nameMethod = clazz.getMethod("name");
        for (Object item : enums) {
            writer.newLine();
            writer.append(nameMethod.invoke(item).toString());
            appendEnumFieldValues(writer, item, fields);
            writer.append(",");
        }
        writer.newLine();
        writer.append(';');
        for (Field field : fields) {
            writer.newLine();
            writer.append(field.toGenericString().replace(clazz.getName().concat("."), "")).append(";");
        }
    }

    private static void appendEnumFieldValues(BufferedWriter writer, Object enumItem, List<Field> enumFields) throws Exception {
        if (enumFields.isEmpty()) {
            return;
        }
        writer.append("(");
        for (int i = 0; i < enumFields.size(); i++) {
            if (i > 0) {
                writer.append(", ");
            }
            Field enumField = enumFields.get(i);
            enumField.setAccessible(true);
            Object value = enumField.get(enumItem);
            if (value instanceof CharSequence) {
                writer.append("\"").append(value.toString()).append("\"");
            } else if (value instanceof Long) {
                writer.append(value.toString()).append("L");
            } else if (value instanceof Short) {
                writer.append("(short)").append(value.toString());
            } else { //TODO
                writer.append(value.toString());
            }
        }
        writer.append(")");
    }

    private void doWriteContent4Pojo(BufferedWriter writer, Class<?> clazz) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                if (field.getName().equals("serialVersionUID")) {
                    field.setAccessible(true);
                    writer.newLine();
                    writer.append("private static final long serialVersionUID = ").append(field.get(clazz).toString()).append("L;");
                }
            } else {
                writer.newLine();
                writer.append(field.toGenericString().replace(clazz.getName().concat("."), "")).append(";");
                this.decompile(field.getGenericType());
            }
        }
    }

    private boolean isIgnored(Class<?> clazz) {
        if (this.classMap.containsKey(clazz)) {
            return true;
        }
        String className = clazz.getName();
        for (String basePackage : this.basePackages) {
            if (className.startsWith(basePackage)) {
                return false;
            }
        }
        return true;
    }
}
