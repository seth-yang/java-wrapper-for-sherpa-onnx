package org.dreamwork.tools.onnx.tts.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.text.SimpleDateFormat;

@SuppressWarnings ("unused")
public class JsonHelper {
    public static JsonMapper.Builder jacksonSetup (JsonMapper.Builder builder) {
        return builder
                // 配置线程安全的设置
                .disable (DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable (MapperFeature.DEFAULT_VIEW_INCLUSION)
                .disable (SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable (SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
                .defaultPropertyInclusion (JsonInclude.Value.ALL_NON_NULL)
                .enable (JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .enable (JsonReadFeature.ALLOW_RS_CONTROL_CHAR)

                // 配置支持 public 字段
                .visibility (PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .visibility (PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .visibility (PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
                .defaultDateFormat (new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss"));
    }

    public static ObjectMapper createJackson () {
        return jacksonSetup (JsonMapper.builder ()).build ();
    }

    public static String toJson (ObjectMapper jackson, Object obj) {
        try {
            return jackson.writeValueAsString (obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException (ex);
        }
    }

    public static String toJson (Object obj) {
        return toJson (jackson, obj);
    }

    public static<T> T fromJson (String json, Class<T> clazz) {
        try {
            return jackson.readValue (json, clazz);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException (ex);
        }
    }

    public static<T> T fromJson (String json, TypeReference<T> ref) {
        try {
            return jackson.readValue (json, ref);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException (ex);
        }
    }

    public static ObjectMapper jackson = createJackson ();
}