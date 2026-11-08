/* nan_jni.c - JNI bridge for org.nan0s.vm.NanNative (static externals; handle = nan_vm* as jlong). Thin: no logic. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "../core/nan16.h"
#include "../core/nan_net_driver.h"

#define VM(h) ((nan_vm *)(intptr_t)(h))
#define FN(n) Java_org_nan0s_vm_NanNative_##n

JNIEXPORT jlong JNICALL FN(create)(JNIEnv *e, jclass c) { (void)e; (void)c; return (jlong)(intptr_t)nan_create(); }
JNIEXPORT void JNICALL FN(destroy)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; nan_destroy(VM(h)); }
JNIEXPORT void JNICALL FN(reset)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; nan_reset(VM(h)); }

/* returns the detected kind ("floppy"|"nan-text"|"words"|"state") or null on failure */
JNIEXPORT jstring JNICALL FN(load)(JNIEnv *e, jclass c, jlong h, jbyteArray data) {
  (void)c; if (!data) return NULL;
  jsize n = (*e)->GetArrayLength(e, data); jbyte *p = (*e)->GetByteArrayElements(e, data, NULL); if (!p) return NULL;
  const char *kind = ""; int rc = nan_load_auto(VM(h), (const uint8_t *)p, (size_t)n, &kind);
  (*e)->ReleaseByteArrayElements(e, data, p, JNI_ABORT);
  return rc == 0 ? (*e)->NewStringUTF(e, kind) : NULL;
}
JNIEXPORT jbyteArray JNICALL FN(readBytes)(JNIEnv *e, jclass c, jlong h, jint address, jint length) {
  (void)c; if (address < 0 || length < 0 || length > 1048576) return NULL;
  jbyteArray out = (*e)->NewByteArray(e, length); if (!out) return NULL;
  if (length == 0) return out;
  jbyte *buf = (jbyte *)malloc((size_t)length);
  if (!buf) return NULL;
  int rc = nan_read_bytes(VM(h), (uint32_t)address, (uint8_t *)buf, (size_t)length);
  if (rc == 0) (*e)->SetByteArrayRegion(e, out, 0, length, buf);
  free(buf);
  return rc == 0 ? out : NULL;
}
/* Read-only full-word transport; do not expose an unchecked bank write.
   JNI maps word-index/word-count to raw eight-byte NaN16 words, preserving
   both NaN faces. A kernel/NANNET lease remains mandatory for head writes. */
JNIEXPORT jbyteArray JNICALL FN(readWordsRaw)(JNIEnv *e, jclass c, jlong h,
                                             jint firstWord, jint wordCount) {
  (void)c;
  if (firstWord < 0 || wordCount < 0 || wordCount > 67838) return NULL;
  size_t bytes = (size_t)wordCount * 8u;
  jbyteArray out = (*e)->NewByteArray(e, (jsize)bytes);
  if (!out) return NULL;
  if (wordCount == 0) return out;
  uint8_t *buffer = (uint8_t *)malloc(bytes);
  if (!buffer) return NULL;
  int rc = nan_read_words_raw(VM(h), (uint32_t)firstWord,
                              buffer, (size_t)wordCount);
  if (rc == 0)
    (*e)->SetByteArrayRegion(e, out, 0, (jsize)bytes, (const jbyte *)buffer);
  free(buffer);
  return rc == 0 ? out : NULL;
}

JNIEXPORT jboolean JNICALL FN(stageNetworkFrame)(JNIEnv *e, jclass c, jlong h, jint address, jbyteArray data) {
  (void)c; if (!data || address < 0) return JNI_FALSE;
  jsize n = (*e)->GetArrayLength(e, data);
  jbyte *p = (*e)->GetByteArrayElements(e, data, NULL); if (!p) return JNI_FALSE;
  int rc = nan_net_stage_frame(VM(h), (uint32_t)address, (const uint8_t *)p, (size_t)n);
  (*e)->ReleaseByteArrayElements(e, data, p, JNI_ABORT);
  return rc == 0 ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jbyteArray JNICALL FN(extractNetworkFrame)(JNIEnv *e, jclass c, jlong h, jint address, jint length) {
  (void)c; if (address < 0 || length < 36 || length > (jint)NAN_NET_MAX_FRAME) return NULL;
  uint8_t *buf = (uint8_t *)malloc((size_t)length); if (!buf) return NULL;
  int rc = nan_net_extract_frame(VM(h), (uint32_t)address, buf, (size_t)length, (size_t)length);
  jbyteArray out = NULL;
  if (rc == 0) { out = (*e)->NewByteArray(e, length); if (out) (*e)->SetByteArrayRegion(e, out, 0, length, (const jbyte *)buf); }
  free(buf); return out;
}
JNIEXPORT jboolean JNICALL FN(writeBytes)(JNIEnv *e, jclass c, jlong h, jint address, jbyteArray data) {
  (void)c; if (!data || address < 0) return JNI_FALSE;
  jsize n = (*e)->GetArrayLength(e, data);
  jbyte *p = (*e)->GetByteArrayElements(e, data, NULL); if (!p) return JNI_FALSE;
  int rc = nan_write_bytes(VM(h), (uint32_t)address, (const uint8_t *)p, (size_t)n);
  (*e)->ReleaseByteArrayElements(e, data, p, JNI_ABORT);
  return rc == 0 ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL FN(getReg)(JNIEnv *e, jclass c, jlong h, jint index) {
  (void)e; (void)c; return (jint)nan_reg(VM(h), index);
}
JNIEXPORT void JNICALL FN(setReg)(JNIEnv *e, jclass c, jlong h, jint index, jint value) {
  (void)e; (void)c; nan_set_reg(VM(h), index, (uint32_t)value);
}
JNIEXPORT void JNICALL FN(setPc)(JNIEnv *e, jclass c, jlong h, jint pc) {
  (void)e; (void)c; if (pc >= 0) nan_set_pc(VM(h), (uint32_t)pc);
}
JNIEXPORT jint JNICALL FN(run)(JNIEnv *e, jclass c, jlong h, jint steps) { (void)e; (void)c; return nan_run(VM(h), steps > 0 ? (uint32_t)steps : 0u); }
JNIEXPORT void JNICALL FN(setTime)(JNIEnv *e, jclass c, jlong h, jint ms) { (void)e; (void)c; nan_set_time_ms(VM(h), (uint32_t)ms); }
JNIEXPORT void JNICALL FN(keyChar)(JNIEnv *e, jclass c, jlong h, jint ch) { (void)e; (void)c; nan_key_char(VM(h), ch); }
JNIEXPORT void JNICALL FN(keyEvent)(JNIEnv *e, jclass c, jlong h, jint sc, jboolean down) { (void)e; (void)c; nan_key_event(VM(h), sc, down ? 1 : 0); }
JNIEXPORT jint JNICALL FN(rawKeyboard)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return nan_raw_keyboard(VM(h)); }
JNIEXPORT jint JNICALL FN(displayMode)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return nan_display_mode(VM(h)); }
JNIEXPORT jint JNICALL FN(displayWidth)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; int w, hh; nan_display_size(VM(h), &w, &hh); return w; }
JNIEXPORT jint JNICALL FN(displayHeight)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; int w, hh; nan_display_size(VM(h), &w, &hh); return hh; }
JNIEXPORT jint JNICALL FN(dirty)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return (jint)nan_dirty(VM(h)); }
JNIEXPORT jint JNICALL FN(speakerHz)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return (jint)nan_speaker_hz(VM(h)); }
JNIEXPORT jlong JNICALL FN(steps)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return (jlong)nan_steps(VM(h)); }
JNIEXPORT jstring JNICALL FN(trap)(JNIEnv *e, jclass c, jlong h) { (void)c; return (*e)->NewStringUTF(e, nan_trap_text(VM(h))); }
JNIEXPORT jint JNICALL FN(pc)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; return (jint)nan_pc(VM(h)); }

/* text screen: 2000 ints (char | attr<<8) */
JNIEXPORT jboolean JNICALL FN(textCells)(JNIEnv *e, jclass c, jlong h, jintArray out) {
  (void)c; if (!out || (*e)->GetArrayLength(e, out) < 2000) return JNI_FALSE;
  jint t[2000]; for (int i = 0; i < 2000; i++) t[i] = nan_text_cell(VM(h), i);
  (*e)->SetIntArrayRegion(e, out, 0, 2000, t); return JNI_TRUE;
}
/* the 16 text colours through the DAC (0xAARRGGBB) */
JNIEXPORT jboolean JNICALL FN(textPalette)(JNIEnv *e, jclass c, jlong h, jintArray out) {
  (void)c; if (!out || (*e)->GetArrayLength(e, out) < 16) return JNI_FALSE;
  jint t[16]; for (int i = 0; i < 16; i++) t[i] = (jint)nan_text_color_vm(VM(h), i);
  (*e)->SetIntArrayRegion(e, out, 0, 16, t); return JNI_TRUE;
}
/* graphics: fills out[w*h] with ARGB; false if not in a graphics mode or the array is too small */
JNIEXPORT jboolean JNICALL FN(render)(JNIEnv *e, jclass c, jlong h, jintArray out) {
  (void)c; if (!out) return JNI_FALSE; jsize n = (*e)->GetArrayLength(e, out);
  jint *p = (*e)->GetIntArrayElements(e, out, NULL); if (!p) return JNI_FALSE;
  int rc = nan_render_gfx(VM(h), (uint32_t *)p, (size_t)n);
  (*e)->ReleaseIntArrayElements(e, out, p, rc == 0 ? 0 : JNI_ABORT);
  return rc == 0;
}
/* COM1/PUTC/trap text accumulated since the last call (raw bytes, may be empty) */
JNIEXPORT jbyteArray JNICALL FN(serial)(JNIEnv *e, jclass c, jlong h) {
  (void)c; char buf[4096]; size_t n = nan_serial_read(VM(h), buf, sizeof buf);
  jbyteArray a = (*e)->NewByteArray(e, (jsize)n); if (a && n) (*e)->SetByteArrayRegion(e, a, 0, (jsize)n, (const jbyte *)buf);
  return a;
}
JNIEXPORT jbyteArray JNICALL FN(saveState)(JNIEnv *e, jclass c, jlong h) {
  (void)c; size_t n = nan_state_size(VM(h)); if (!n) return NULL;
  uint8_t *b = (uint8_t *)malloc(n); if (!b) return NULL;
  size_t w = nan_save_state(VM(h), b, n); jbyteArray a = NULL;
  if (w) { a = (*e)->NewByteArray(e, (jsize)w); if (a) (*e)->SetByteArrayRegion(e, a, 0, (jsize)w, (const jbyte *)b); }
  free(b); return a;
}
JNIEXPORT jboolean JNICALL FN(restoreState)(JNIEnv *e, jclass c, jlong h, jbyteArray data) {
  (void)c; if (!data) return JNI_FALSE; jsize n = (*e)->GetArrayLength(e, data); jbyte *p = (*e)->GetByteArrayElements(e, data, NULL); if (!p) return JNI_FALSE;
  int rc = nan_restore_state(VM(h), (const uint8_t *)p, (size_t)n); (*e)->ReleaseByteArrayElements(e, data, p, JNI_ABORT); return rc == 0;
}
/* disk image by file descriptor (ParcelFileDescriptor.detachFd / dup); size in bytes */
JNIEXPORT void JNICALL FN(attachDiskFd)(JNIEnv *e, jclass c, jlong h, jint fd, jlong size) { (void)e; (void)c; nan_attach_disk_fd(VM(h), fd, (size_t)size); }
JNIEXPORT void JNICALL FN(detachDisk)(JNIEnv *e, jclass c, jlong h) { (void)e; (void)c; nan_detach_disk(VM(h)); }
