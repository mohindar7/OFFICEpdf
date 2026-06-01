package com.example.officepdf

@JsFun("(filesArray) => window.pdfMerge(filesArray)")
external fun jsPdfMerge(filesArray: JsAny): JsAny

@JsFun("(fileData, ranges) => window.pdfSplit(fileData, ranges)")
external fun jsPdfSplit(fileData: JsAny, ranges: String): JsAny

@JsFun("(fileData, watermarkText) => window.pdfWatermark(fileData, watermarkText)")
external fun jsPdfWatermark(fileData: JsAny, watermarkText: String): JsAny

@JsFun("(fileData, rotationDegrees) => window.pdfRotate(fileData, rotationDegrees)")
external fun jsPdfRotate(fileData: JsAny, rotationDegrees: Int): JsAny

@JsFun("(fileData, password) => window.pdfProtect(fileData, password)")
external fun jsPdfProtect(fileData: JsAny, password: String): JsAny

@JsFun("(fileData, password) => window.pdfUnlock(fileData, password)")
external fun jsPdfUnlock(fileData: JsAny, password: String): JsAny

@JsFun("(fileData, keptPageIndices) => window.pdfOrganize(fileData, keptPageIndices)")
external fun jsPdfOrganize(fileData: JsAny, keptPageIndices: JsAny): JsAny

@JsFun("(fileData) => window.pdfAddPageNumbers(fileData)")
external fun jsPdfAddPageNumbers(fileData: JsAny): JsAny

@JsFun("(fileData, left, right, top, bottom) => window.pdfCrop(fileData, left, right, top, bottom)")
external fun jsPdfCrop(fileData: JsAny, left: Float, right: Float, top: Float, bottom: Float): JsAny

@JsFun("(imagesArray) => window.pdfImagesToPdf(imagesArray)")
external fun jsPdfImagesToPdf(imagesArray: JsAny): JsAny

@JsFun("(fileData, keptPageIndices, pageRotations, pageCrops, overlays, watermarkText, hasPageNumbers) => window.pdfProcessWorkspace(fileData, keptPageIndices, pageRotations, pageCrops, overlays, watermarkText, hasPageNumbers)")
external fun jsPdfProcessWorkspace(
    fileData: JsAny,
    keptPageIndices: JsAny,
    pageRotations: JsAny,
    pageCrops: JsAny,
    overlays: JsAny,
    watermarkText: String?,
    hasPageNumbers: Boolean
): JsAny

@JsFun("(fileData) => window.pdfRenderPages(fileData)")
external fun jsPdfRenderPages(fileData: JsAny): JsAny

@JsFun("(fileData) => window.pdfIsProtected(fileData)")
external fun jsPdfIsProtected(fileData: JsAny): JsAny

@JsFun("(jsVal) => !!jsVal")
external fun jsToKotlinBoolean(jsVal: JsAny): Boolean


class WebPlatformFile(
    override val name: String,
    override val size: Long,
    override val path: String,
    private val bytes: ByteArray
) : PlatformFile {
    override suspend fun readBytes(): ByteArray = bytes
}

class WebPdfEngine : PdfEngine {

    override suspend fun mergePdfs(files: List<PlatformFile>): ByteArray {
        val jsArray = createJsArray()
        for (f in files) {
            pushJsArray(jsArray, f.readBytes().toJsUint8Array())
        }
        val promise = jsPdfMerge(jsArray)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun splitPdf(file: PlatformFile, ranges: String): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfSplit(bytes, ranges)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun addWatermark(file: PlatformFile, watermarkText: String): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfWatermark(bytes, watermarkText)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun rotatePdf(file: PlatformFile, rotationDegrees: Int): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfRotate(bytes, rotationDegrees)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun protectPdf(file: PlatformFile, password: String): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfProtect(bytes, password)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun unlockPdf(file: PlatformFile, password: String): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfUnlock(bytes, password)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun organizePdf(file: PlatformFile, keptPageIndices: List<Int>): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val jsIndices = createJsArray()
        for (idx in keptPageIndices) {
            pushJsArrayInt(jsIndices, idx)
        }
        val promise = jsPdfOrganize(bytes, jsIndices)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun addPageNumbers(file: PlatformFile): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfAddPageNumbers(bytes)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun cropPdf(
        file: PlatformFile,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ): ByteArray {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfCrop(bytes, left, right, top, bottom)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun imagesToPdf(imageFiles: List<PlatformFile>): ByteArray {
        val jsArray = createJsArray()
        for (f in imageFiles) {
            pushJsArray(jsArray, f.readBytes().toJsUint8Array())
        }
        val promise = jsPdfImagesToPdf(jsArray)
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun processPdfWorkspace(
        inputFile: PlatformFile,
        keptPageIndices: List<Int>,
        pageRotations: Map<Int, Int>,
        pageCrops: Map<Int, List<Float>>,
        overlays: List<PageOverlay>,
        watermarkText: String?,
        hasPageNumbers: Boolean
    ): ByteArray {
        val bytes = inputFile.readBytes().toJsUint8Array()
        
        val jsIndices = createJsArray()
        for (idx in keptPageIndices) {
            pushJsArrayInt(jsIndices, idx)
        }
        
        val jsRotations = createJsObject()
        for ((k, v) in pageRotations) {
            setJsObjectPropertyInt(jsRotations, k.toString(), v)
        }
        
        val jsCrops = createJsObject()
        for ((k, v) in pageCrops) {
            val jsCropArray = createJsArray()
            for (f in v) {
                pushJsArrayFloat(jsCropArray, f)
            }
            setJsObjectProperty(jsCrops, k.toString(), jsCropArray)
        }
        
        val jsOverlays = createJsArray()
        for (o in overlays) {
            val jsOverlay = createJsObject()
            setJsObjectPropertyString(jsOverlay, "id", o.id)
            setJsObjectPropertyString(jsOverlay, "type", o.type)
            setJsObjectPropertyString(jsOverlay, "text", o.text)
            setJsObjectPropertyString(jsOverlay, "imagePath", o.imagePath)
            if (o.imageBytes != null) {
                setJsObjectProperty(jsOverlay, "imageBytes", o.imageBytes.toJsUint8Array())
            }
            setJsObjectPropertyFloat(jsOverlay, "xFraction", o.xFraction)
            setJsObjectPropertyFloat(jsOverlay, "yFraction", o.yFraction)
            setJsObjectPropertyFloat(jsOverlay, "widthFraction", o.widthFraction)
            setJsObjectPropertyFloat(jsOverlay, "heightFraction", o.heightFraction)
            setJsObjectPropertyInt(jsOverlay, "pageNumber", o.pageNumber)
            setJsObjectPropertyFloat(jsOverlay, "rotation", o.rotation)
            pushJsArray(jsOverlays, jsOverlay)
        }
        
        val promise = jsPdfProcessWorkspace(
            bytes,
            jsIndices,
            jsRotations,
            jsCrops,
            jsOverlays,
            watermarkText,
            hasPageNumbers
        )
        val result = awaitPromise(promise)
        return jsUint8ArrayToKotlinByteArray(result)
    }

    override suspend fun renderPdfPages(file: PlatformFile): List<ByteArray> {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfRenderPages(bytes)
        val jsArray = awaitPromise(promise)
        val len = getJsArrayLength(jsArray)
        val list = mutableListOf<ByteArray>()
        for (i in 0 until len) {
            val element = getJsArrayElement(jsArray, i)
            list.add(jsUint8ArrayToKotlinByteArray(element))
        }
        return list
    }

    override suspend fun isProtected(file: PlatformFile): Boolean {
        val bytes = file.readBytes().toJsUint8Array()
        val promise = jsPdfIsProtected(bytes)
        val result = awaitPromise(promise)
        return jsToKotlinBoolean(result)
    }
}
