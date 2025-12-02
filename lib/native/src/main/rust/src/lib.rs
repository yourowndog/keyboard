use dummy;

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint};
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_TestKt_dummyAdd(
    _env: JNIEnv,
    _class: JClass,
    a: jint,
    b: jint,
) -> jint {
    dummy::addnumbers(a, b)
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_TestKt_checkModelPath(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jboolean {
    let input: String = env.get_string(&path).expect("Couldn't get java string!").into();
    let exists = std::path::Path::new(&input).exists();
    if exists { 1 } else { 0 }
}
