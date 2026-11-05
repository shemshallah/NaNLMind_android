# N16TXT corpus format v1

Status: v1 guest implementation is present; UTF-8 and cross-platform conformance expansion remains in progress.

## Purpose

.n16txt is a strict, portable UTF-8 text corpus container. It is intentionally human-editable and can be generated from an ordinary .txt file. It contains training data only: no executable words, model deltas, route commands, or policy settings.

## Wire/file grammar

A file is UTF-8 without a byte-order mark:

~~~text
N16TXT/1
The first training sentence.
A second example sentence.
A third example.
~~~

- The first line must be exactly N16TXT/1.
- Each subsequent non-empty line is one record.
- LF and CRLF are accepted; NaN16 canonicalizes both to LF.
- Empty lines are ignored.
- A final LF is optional.
- A record is limited to 4096 UTF-8 bytes.
- A corpus is limited to 1,048,576 bytes after canonicalization.
- NUL, malformed UTF-8, isolated continuation bytes, overlong encodings, UTF-16 surrogates, and Unicode noncharacters are rejected.
- Tab is allowed. Other C0/C1 control characters are rejected.
- The codec does not execute markup, interpret directives, or treat text prefixes as commands.
- A plain .txt file may be imported by prepending the required header in the NaN16 intake flow; the source file is not modified.

## Canonical record stream

The codec emits a NaN16-owned record stream:

- codec version: 1
- record count: u32
- for each record: u32 byte length, then exact UTF-8 bytes
- corpus digest: CRC-32 of the canonical record stream

The stream is an internal interchange form, not an executable NaN16 program. The NaN16 codec owns validation, canonicalization, record boundaries, and digest calculation. A C file driver may only supply bounded bytes and report I/O errors.

## Training determinism

The sample scheduler consumes the canonical record stream in stable record order, removes exact duplicates, then applies deterministic epoch shuffling using the NaN16 policy seed. The seed is derived from the policy version, corpus digest, and epoch. Both runner and Android must produce the same canonical stream and sample-order digest for the same input.

## Required conformance vectors

- LF and CRLF versions of the same file produce the same canonical digest.
- Optional final LF does not change the digest.
- Blank lines do not create records.
- A 4096-byte record is accepted; 4097 bytes is rejected.
- A corpus at 1 MiB is accepted; larger is rejected before allocation.
- Invalid UTF-8 and forbidden control characters are rejected.
- Missing or altered header is rejected.
- Text resembling NaN16 opcodes, network headers, or routing commands remains ordinary text data.
- No malformed input may modify the active model or committed corpus store.
