#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"FlightTraceBridge",__VA_ARGS__)
JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *vm,char *options,void *reserved){
 (void)reserved;
 if(!options||strlen(options)>1024){LOG("Invalid bridge options");return JNI_ERR;}
 char *copy=strdup(options);if(!copy)return JNI_ERR;char *divider=strchr(copy,'|');
 if(!divider){LOG("Agent options missing delimiter");free(copy);return JNI_ERR;}*divider=0;const char *secret=divider+1;
 if(strncmp(copy,"/data/app/",10)||strlen(secret)!=32){LOG("Agent options rejected");free(copy);return JNI_ERR;}
 JNIEnv *e=0;if((*vm)->GetEnv(vm,(void**)&e,JNI_VERSION_1_6)!=JNI_OK){LOG("JNI environment unavailable");free(copy);return JNI_ERR;}
 jclass at=(*e)->FindClass(e,"android/app/ActivityThread");
 jmethodID ca=(*e)->GetStaticMethodID(e,at,"currentApplication","()Landroid/app/Application;");
 jobject app=(*e)->CallStaticObjectMethod(e,at,ca);
 if((*e)->ExceptionCheck(e)||!app){(*e)->ExceptionClear(e);LOG("Current application unavailable");free(copy);return JNI_ERR;}
 jclass ac=(*e)->GetObjectClass(e,app);jmethodID gc=(*e)->GetMethodID(e,ac,"getClassLoader","()Ljava/lang/ClassLoader;");
 jobject parent=(*e)->CallObjectMethod(e,app,gc);
 jclass dc=(*e)->FindClass(e,"dalvik/system/DexClassLoader");
 jmethodID ctor=(*e)->GetMethodID(e,dc,"<init>","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
 jstring path=(*e)->NewStringUTF(e,copy);jobject loader=(*e)->NewObject(e,dc,ctor,path,NULL,NULL,parent);
 jmethodID load=(*e)->GetMethodID(e,dc,"loadClass","(Ljava/lang/String;)Ljava/lang/Class;");
 jstring name=(*e)->NewStringUTF(e,"org.openkatmai.flighttrace.bridge.BridgeEntry");
 jclass entry=(jclass)(*e)->CallObjectMethod(e,loader,load,name);
 if((*e)->ExceptionCheck(e)||!entry){(*e)->ExceptionClear(e);LOG("Bridge class load failed");free(copy);return JNI_ERR;}
 jmethodID start=(*e)->GetStaticMethodID(e,entry,"start","(Ljava/lang/ClassLoader;Landroid/content/Context;Ljava/lang/String;)Ljava/lang/String;");
 jstring token=(*e)->NewStringUTF(e,secret);
 jstring result=(jstring)(*e)->CallStaticObjectMethod(e,entry,start,parent,app,token);
 if((*e)->ExceptionCheck(e)||!result){if((*e)->ExceptionCheck(e))(*e)->ExceptionDescribe(e);(*e)->ExceptionClear(e);LOG("Bridge unavailable or already bound; existing stream preserved");free(copy);return JNI_ERR;}
 const char *message=(*e)->GetStringUTFChars(e,result,0);if(message){LOG("%s",message);(*e)->ReleaseStringUTFChars(e,result,message);}
 free(copy);return JNI_OK;
}
