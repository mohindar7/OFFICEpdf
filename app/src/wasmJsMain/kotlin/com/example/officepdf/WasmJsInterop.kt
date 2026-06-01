package com.example.officepdf

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@JsFun("(size) => new Uint8Array(size)")
external fun createUint8Array(size: Int): JsAny

@JsFun("(array, index, value) => { array[index] = value; }")
external fun setUint8ArrayElement(array: JsAny, index: Int, value: Int)

@JsFun("(array) => array.length")
external fun getUint8ArrayLength(array: JsAny): Int

@JsFun("(array, index) => array[index]")
external fun getUint8ArrayElement(array: JsAny, index: Int): Int

@JsFun("() => []")
external fun createJsArray(): JsAny

@JsFun("(array, item) => { array.push(item); }")
external fun pushJsArray(array: JsAny, item: JsAny)

@JsFun("(array, index) => array[index]")
external fun getJsArrayElement(array: JsAny, index: Int): JsAny

@JsFun("(array) => array.length")
external fun getJsArrayLength(array: JsAny): Int

@JsFun("() => ({})")
external fun createJsObject(): JsAny

@JsFun("(obj, key, value) => { obj[key] = value; }")
external fun setJsObjectProperty(obj: JsAny, key: String, value: JsAny)

@JsFun("(obj, key, value) => { obj[key] = value; }")
external fun setJsObjectPropertyInt(obj: JsAny, key: String, value: Int)

@JsFun("(obj, key, value) => { obj[key] = value; }")
external fun setJsObjectPropertyFloat(obj: JsAny, key: String, value: Float)

@JsFun("(obj, key, value) => { obj[key] = value; }")
external fun setJsObjectPropertyString(obj: JsAny, key: String, value: String)

@JsFun("(array, item) => { array.push(item); }")
external fun pushJsArrayInt(array: JsAny, item: Int)

@JsFun("(array, item) => { array.push(item); }")
external fun pushJsArrayFloat(array: JsAny, item: Float)

@JsFun("(promise, onResolve, onReject) => { promise.then(onResolve).catch(onReject); }")
external fun thenPromise(promise: JsAny, onResolve: (JsAny) -> Unit, onReject: (JsAny) -> Unit)

fun ByteArray.toJsUint8Array(): JsAny {
    val size = this.size
    val jsArray = createUint8Array(size)
    for (i in 0 until size) {
        setUint8ArrayElement(jsArray, i, this[i].toInt() and 0xFF)
    }
    return jsArray
}

fun jsUint8ArrayToKotlinByteArray(jsArray: JsAny): ByteArray {
    val size = getUint8ArrayLength(jsArray)
    val byteArray = ByteArray(size)
    for (i in 0 until size) {
        byteArray[i] = getUint8ArrayElement(jsArray, i).toByte()
    }
    return byteArray
}

suspend fun awaitPromise(promise: JsAny): JsAny = suspendCancellableCoroutine { continuation ->
    thenPromise(promise,
        onResolve = { result ->
            continuation.resume(result)
        },
        onReject = { error ->
            continuation.resumeWithException(Exception(error.toString()))
        }
    )
}

@JsFun("(name, bytes, mimeType) => { const blob = new Blob([bytes], {type: mimeType}); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = name; document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url); }")
external fun triggerDownload(name: String, bytes: JsAny, mimeType: String)

@JsFun("(accept, multiple, onPicked) => { const input = document.createElement('input'); input.type = 'file'; input.accept = accept; input.multiple = multiple; input.onchange = async () => { const result = []; for (let i = 0; i < input.files.length; i++) { const file = input.files[i]; const buffer = await file.arrayBuffer(); result.push({ name: file.name, size: file.size, bytes: new Uint8Array(buffer) }); } onPicked(result); }; input.click(); }")
external fun jsPickFiles(accept: String, multiple: Boolean, onPicked: (JsAny) -> Unit)

@JsFun("(fileObj) => fileObj.name")
external fun getJsFileName(fileObj: JsAny): String

@JsFun("(fileObj) => fileObj.size")
external fun getJsFileSize(fileObj: JsAny): Double

@JsFun("(fileObj) => fileObj.bytes")
external fun getJsFileBytes(fileObj: JsAny): JsAny

