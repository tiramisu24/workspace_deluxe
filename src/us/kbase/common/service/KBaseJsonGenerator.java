package us.kbase.common.service;

import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;

/**
 * This extension of UTF8JsonGenerator helps to store very large text
 * data (from JsonByteString) into output stream. Most of private 
 * methods were copied from UTF8JsonGenerator without change (because
 * we don't have access to private original code). 
 */
public class KBaseJsonGenerator extends UTF8JsonGenerator {
    private final static byte BYTE_u = (byte) 'u';
    private final static byte BYTE_0 = (byte) '0';
    private final static byte BYTE_BACKSLASH = (byte) '\\';
    private final static byte BYTE_QUOTE = (byte) '"';
    private final static byte[] HEX_CHARS = CharTypes.copyHexBytes();

    public KBaseJsonGenerator(IOContext ctxt, int features, 
    		ObjectCodec codec, OutputStream out) {
    	super(ctxt, features, codec, out);
    }
    
    /**
     * Method opens and close quoted JSON text value.
     */
    public void writeQuote(boolean firstQuote) 
    		throws JsonGenerationException, IOException {
        if (firstQuote)
        	_verifyValueWrite("write text value");
        if (_outputTail >= _outputEnd) {
            _flushBuffer();
        }
        _outputBuffer[_outputTail++] = BYTE_QUOTE;
    }
    
    /**
     * Method adds next part of JSON text value.
     */
    public void writeStringNoQuotes(char[] text, int offset, int len)
        throws IOException, JsonGenerationException {
        // One or multiple segments?
        if (len <= _outputMaxContiguous) {
            if ((_outputTail + len) > _outputEnd) { // caller must ensure enough space
                _flushBuffer();
            }
            _writeStringSegment(text, offset, len);
        } else {
            _writeStringSegments(text, offset, len);
        }
    }

    private final void _writeStringSegments(char[] cbuf, int offset, int totalLen)
    		throws IOException, JsonGenerationException {
    	do {
    		int len = Math.min(_outputMaxContiguous, totalLen);
    		if ((_outputTail + len) > _outputEnd) { // caller must ensure enough space
    			_flushBuffer();
    		}
    		_writeStringSegment(cbuf, offset, len);
    		offset += len;
    		totalLen -= len;
    	} while (totalLen > 0);
    }
    
    private final void _writeStringSegment(char[] cbuf, int offset, int len)
    		throws IOException, JsonGenerationException {
    	len += offset; // becomes end marker, then

    	int outputPtr = _outputTail;
    	final byte[] outputBuffer = _outputBuffer;
    	final int[] escCodes = _outputEscapes;

    	while (offset < len) {
    		int ch = cbuf[offset];
    		// note: here we know that (ch > 0x7F) will cover case of escaping non-ASCII too:
    		if (ch > 0x7F || escCodes[ch] != 0) {
    			break;
    		}
    		outputBuffer[outputPtr++] = (byte) ch;
    		++offset;
    	}
    	_outputTail = outputPtr;
    	if (offset < len) {
    		if (_characterEscapes != null) {
    			_writeCustomStringSegment2(cbuf, offset, len);
    		} else if (_maximumNonEscapedChar == 0) {
    			_writeStringSegment2(cbuf, offset, len);
    		} else {
    			_writeStringSegmentASCII2(cbuf, offset, len);
    		}
    	}
    }

    private void _writeCustomStringSegment2(final char[] cbuf, int offset, final int end)
    		throws IOException, JsonGenerationException {
    	// Ok: caller guarantees buffer can have room; but that may require flushing:
    	if ((_outputTail +  6 * (end - offset)) > _outputEnd) {
    		_flushBuffer();
    	}
    	int outputPtr = _outputTail;

    	final byte[] outputBuffer = _outputBuffer;
    	final int[] escCodes = _outputEscapes;
    	// may or may not have this limit
    	final int maxUnescaped = (_maximumNonEscapedChar <= 0) ? 0xFFFF : _maximumNonEscapedChar;
    	final CharacterEscapes customEscapes = _characterEscapes; // non-null

    	while (offset < end) {
    		int ch = cbuf[offset++];
    		if (ch <= 0x7F) {
    			if (escCodes[ch] == 0) {
    				outputBuffer[outputPtr++] = (byte) ch;
    				continue;
    			}
    			int escape = escCodes[ch];
    			if (escape > 0) { // 2-char escape, fine
    				outputBuffer[outputPtr++] = BYTE_BACKSLASH;
    				outputBuffer[outputPtr++] = (byte) escape;
    			} else if (escape == CharacterEscapes.ESCAPE_CUSTOM) {
    				SerializableString esc = customEscapes.getEscapeSequence(ch);
    				if (esc == null) {
    					_reportError("Invalid custom escape definitions; custom escape not found for character code 0x"
    							+Integer.toHexString(ch)+", although was supposed to have one");
    				}
    				outputPtr = _writeCustomEscape(outputBuffer, outputPtr, esc, end-offset);
    			} else {
    				// ctrl-char, 6-byte escape...
    				outputPtr = _writeGenericEscape(ch, outputPtr);
    			}
    			continue;
    		}
    		if (ch > maxUnescaped) { // [JACKSON-102] Allow forced escaping if non-ASCII (etc) chars:
    			outputPtr = _writeGenericEscape(ch, outputPtr);
    			continue;
    		}
    		SerializableString esc = customEscapes.getEscapeSequence(ch);
    		if (esc != null) {
    			outputPtr = _writeCustomEscape(outputBuffer, outputPtr, esc, end-offset);
    			continue;
    		}
    		if (ch <= 0x7FF) { // fine, just needs 2 byte output
    			outputBuffer[outputPtr++] = (byte) (0xc0 | (ch >> 6));
    			outputBuffer[outputPtr++] = (byte) (0x80 | (ch & 0x3f));
    		} else {
    			outputPtr = _outputMultiByteChar(ch, outputPtr);
    		}
    	}
    	_outputTail = outputPtr;
    }

    private int _writeCustomEscape(byte[] outputBuffer, int outputPtr, SerializableString esc, int remainingChars)
    		throws IOException, JsonGenerationException {
    	byte[] raw = esc.asUnquotedUTF8(); // must be escaped at this point, shouldn't double-quote
    	int len = raw.length;
    	if (len > 6) { // may violate constraints we have, do offline
    		return _handleLongCustomEscape(outputBuffer, outputPtr, _outputEnd, raw, remainingChars);
    	}
    	// otherwise will fit without issues, so:
    	System.arraycopy(raw, 0, outputBuffer, outputPtr, len);
    	return (outputPtr + len);
    }

    private int _handleLongCustomEscape(byte[] outputBuffer, int outputPtr, int outputEnd, byte[] raw,
    		int remainingChars) throws IOException, JsonGenerationException {
    	int len = raw.length;
    	if ((outputPtr + len) > outputEnd) {
    		_outputTail = outputPtr;
    		_flushBuffer();
    		outputPtr = _outputTail;
    		if (len > outputBuffer.length) { // very unlikely, but possible...
    			_outputStream.write(raw, 0, len);
    			return outputPtr;
    		}
    		System.arraycopy(raw, 0, outputBuffer, outputPtr, len);
    		outputPtr += len;
    	}
    	// but is the invariant still obeyed? If not, flush once more
    	if ((outputPtr +  6 * remainingChars) > outputEnd) {
    		_flushBuffer();
    		return _outputTail;
    	}
    	return outputPtr;
    }
    
    private final void _writeStringSegment2(final char[] cbuf, int offset, final int end)
    		throws IOException, JsonGenerationException {
    	// Ok: caller guarantees buffer can have room; but that may require flushing:
    	if ((_outputTail +  6 * (end - offset)) > _outputEnd) {
    		_flushBuffer();
    	}

    	int outputPtr = _outputTail;

    	final byte[] outputBuffer = _outputBuffer;
    	final int[] escCodes = _outputEscapes;

    	while (offset < end) {
    		int ch = cbuf[offset++];
    		if (ch <= 0x7F) {
    			if (escCodes[ch] == 0) {
    				outputBuffer[outputPtr++] = (byte) ch;
    				continue;
    			}
    			int escape = escCodes[ch];
    			if (escape > 0) { // 2-char escape, fine
    				outputBuffer[outputPtr++] = BYTE_BACKSLASH;
    			outputBuffer[outputPtr++] = (byte) escape;
    			} else {
    				// ctrl-char, 6-byte escape...
    				outputPtr = _writeGenericEscape(ch, outputPtr);
    			}
    			continue;
    		}
    		if (ch <= 0x7FF) { // fine, just needs 2 byte output
    			outputBuffer[outputPtr++] = (byte) (0xc0 | (ch >> 6));
    			outputBuffer[outputPtr++] = (byte) (0x80 | (ch & 0x3f));
    		} else {
    			outputPtr = _outputMultiByteChar(ch, outputPtr);
    		}
    	}
    	_outputTail = outputPtr;
    }

    private int _writeGenericEscape(int charToEscape, int outputPtr)
    		throws IOException {
    	final byte[] bbuf = _outputBuffer;
    	bbuf[outputPtr++] = BYTE_BACKSLASH;
    	bbuf[outputPtr++] = BYTE_u;
    	if (charToEscape > 0xFF) {
    		int hi = (charToEscape >> 8) & 0xFF;
    		bbuf[outputPtr++] = HEX_CHARS[hi >> 4];
    		bbuf[outputPtr++] = HEX_CHARS[hi & 0xF];
    		charToEscape &= 0xFF;
    	} else {
    		bbuf[outputPtr++] = BYTE_0;
    		bbuf[outputPtr++] = BYTE_0;
    	}
    	// We know it's a control char, so only the last 2 chars are non-0
    	bbuf[outputPtr++] = HEX_CHARS[charToEscape >> 4];
    	bbuf[outputPtr++] = HEX_CHARS[charToEscape & 0xF];
    	return outputPtr;
    }

    private int _outputMultiByteChar(int ch, int outputPtr)
    		throws IOException {
    	byte[] bbuf = _outputBuffer;
    	if (ch >= SURR1_FIRST && ch <= SURR2_LAST) { // yes, outside of BMP; add an escape
    		bbuf[outputPtr++] = BYTE_BACKSLASH;
    		bbuf[outputPtr++] = BYTE_u;

    		bbuf[outputPtr++] = HEX_CHARS[(ch >> 12) & 0xF];
    		bbuf[outputPtr++] = HEX_CHARS[(ch >> 8) & 0xF];
    		bbuf[outputPtr++] = HEX_CHARS[(ch >> 4) & 0xF];
    		bbuf[outputPtr++] = HEX_CHARS[ch & 0xF];
    	} else {
    		bbuf[outputPtr++] = (byte) (0xe0 | (ch >> 12));
    		bbuf[outputPtr++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
    		bbuf[outputPtr++] = (byte) (0x80 | (ch & 0x3f));
    	}
    	return outputPtr;
    }
    
    private final void _writeStringSegmentASCII2(final char[] cbuf, int offset, final int end)
    		throws IOException, JsonGenerationException {
    	// Ok: caller guarantees buffer can have room; but that may require flushing:
    	if ((_outputTail +  6 * (end - offset)) > _outputEnd) {
    		_flushBuffer();
    	}

    	int outputPtr = _outputTail;

    	final byte[] outputBuffer = _outputBuffer;
    	final int[] escCodes = _outputEscapes;
    	final int maxUnescaped = _maximumNonEscapedChar;

    	while (offset < end) {
    		int ch = cbuf[offset++];
    		if (ch <= 0x7F) {
    			if (escCodes[ch] == 0) {
    				outputBuffer[outputPtr++] = (byte) ch;
    				continue;
    			}
    			int escape = escCodes[ch];
    			if (escape > 0) { // 2-char escape, fine
    				outputBuffer[outputPtr++] = BYTE_BACKSLASH;
    			outputBuffer[outputPtr++] = (byte) escape;
    			} else {
    				// ctrl-char, 6-byte escape...
    				outputPtr = _writeGenericEscape(ch, outputPtr);
    			}
    			continue;
    		}
    		if (ch > maxUnescaped) { // [JACKSON-102] Allow forced escaping if non-ASCII (etc) chars:
    			outputPtr = _writeGenericEscape(ch, outputPtr);
    			continue;
    		}
    		if (ch <= 0x7FF) { // fine, just needs 2 byte output
    			outputBuffer[outputPtr++] = (byte) (0xc0 | (ch >> 6));
    			outputBuffer[outputPtr++] = (byte) (0x80 | (ch & 0x3f));
    		} else {
    			outputPtr = _outputMultiByteChar(ch, outputPtr);
    		}
    	}
    	_outputTail = outputPtr;
    }
}
