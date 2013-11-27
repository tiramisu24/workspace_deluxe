package us.kbase.common.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.output.WriterOutputStream;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.NumberInput;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ValueNode;

/**
 * Value node that contains a text stored as byte array (this class was forked from 
 * com.fasterxml.jackson.databind.node.TextNode).
 */
public class JsonByteString extends ValueNode {
	public static final int MIN_BYTE_ARRAY_BLOCK_LEN = 100;
	public static final int MAX_BYTE_ARRAY_BLOCK_LEN = 10000000;  //10m
	
    private final static JsonByteString EMPTY_STRING_NODE = new JsonByteString("");
    private final static Charset charset = Charset.forName("UTF-8");
	private static final char[] buf = new char[2000];

    // Correction that helps to pack text scalars into byte array made by rsutormin
    private Object value; 

    public JsonByteString() {
    	value = null;
    }
    
    public JsonByteString(String v) { 
	    // Correction that helps to pack text scalars into byte array made by rsutormin
    	if (v != null && v.length() > 0) {
    		if (v.length() < 10000000) {  // 10m
    			value = v.getBytes(charset);
    		} else {
    			try {
    				Writer w = createWriter();
    				w.write(v);
    				w.close();
    			} catch (IOException ex) {
    				throw new IllegalStateException(ex);
    			}
    		}
    	} else {
    		value = null;
    	}
    }

    public JsonByteString(byte[][] arr) {
    	value = arr;
    }
    
    public Writer createWriter() {
    	return new OutputStreamWriter(createOutputStream(), charset);
    }
    
    public OutputStream createOutputStream() {
    	if (value != null)
    		throw new IllegalStateException("Value was already defined");
    	return new OutputStream() {
    		private List<byte[]> blocks = new ArrayList<byte[]>();
    		private byte[] curBlock = null;
    		private int posInLastBlock = 0;
			@Override
			public void write(int b) throws IOException {
				if (curBlock == null || posInLastBlock >= curBlock.length)
					addNextBlock();
				curBlock[posInLastBlock] = (byte)b;
				posInLastBlock++;
			}
			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				while (len > 0) {
					if (curBlock == null || posInLastBlock >= curBlock.length)
						addNextBlock();
					int partLen = Math.min(curBlock.length - posInLastBlock, len);
					System.arraycopy(b, off, curBlock, posInLastBlock, partLen);
					posInLastBlock += partLen;
					off += partLen;
					len -= partLen;
				}
			}
			private void addNextBlock() {
				int nextLen;
				if (curBlock != null) {
					nextLen = curBlock.length >= MAX_BYTE_ARRAY_BLOCK_LEN ? MAX_BYTE_ARRAY_BLOCK_LEN : 
						Math.min(MAX_BYTE_ARRAY_BLOCK_LEN, 10 * curBlock.length);
				} else {
					nextLen = MIN_BYTE_ARRAY_BLOCK_LEN;
				}
				curBlock = new byte[nextLen];
				blocks.add(curBlock);
				posInLastBlock = 0;
			}
			@Override
			public void close() throws IOException {
				if (curBlock != null && posInLastBlock < curBlock.length) {
					byte[] newEnd = new byte[posInLastBlock];
					System.arraycopy(curBlock, 0, newEnd, 0, posInLastBlock);
					curBlock = newEnd;
					blocks.set(blocks.size() - 1, curBlock);
				}
				if (blocks.size() == 0) {
					value = null;
				} else if (blocks.size() == 1) {
					value = blocks.get(0);
				} else {
					value = blocks.toArray(new byte[blocks.size()][]);
				}
			}
		};
    }
    
    public static JsonByteString valueOf(String v) {
        if (v == null) {
            return null;
        }
        if (v.length() == 0) {
            return EMPTY_STRING_NODE;
        }
        return new JsonByteString(v);
    }
    
    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.STRING;
    }

    @Override public JsonToken asToken() { 
    	return JsonToken.VALUE_STRING; 
    }

    @Override
    public String textValue() {
        // Correction that helps to pack text scalars into byte array made by rsutormin
    	if (value == null)
    		return "";
    	try {
    		if (getByteLength() < 10000000) {  // 10m
    			byte[] arr = binaryValue();
    			return new String(arr, charset);
    		}
    		final int[] sizeWrapper = new int[] {0};
    		Writer w = new Writer() {
    			@Override
    			public void write(char[] cbuf, int off, int len) throws IOException {
    				sizeWrapper[0] += len;
    			}
    			@Override
    			public void flush() throws IOException {}
    			@Override
    			public void close() throws IOException {}
    		};
    		writeIntoWriter(w, charset);
        	StringWriter sw = new StringWriter(sizeWrapper[0]);
    		writeIntoWriter(sw, charset);
            return sw.toString();
    	} catch (IOException ex) {
    		throw new IllegalStateException(ex);
    	}
    }

    public void writeIntoWriter(Writer w, Charset c) throws IOException {
    	WriterOutputStream wos = new WriterOutputStream(w, c);
		writeIntoStream(wos);
    }
    
    public void writeIntoStream(OutputStream os) throws IOException {
		if (value instanceof byte[]) {
			os.write((byte[])value);
		} else {
			for (byte[] block : (byte[][])value)
				os.write(block);
		}
		os.flush();
    }
    
    public long getByteLength() {
    	long ret = 0;
		if (value instanceof byte[]) {
			ret += ((byte[])value).length;
		} else {
			for (byte[] block : (byte[][])value)
				ret += block.length;
		}
		return ret;
    }
    
    @Override
    public byte[] binaryValue() throws IOException {
        // Correction that helps to pack text scalars into byte array made by rsutormin
    	if (value == null)
    		return new byte[0];
    	long len = getByteLength();
    	if (len > Integer.MAX_VALUE - 8)
    		throw new IOException("Can not allocate byte array. Length is too large: " + len);
    	byte[] ret = new byte[(int)len];
		if (value instanceof byte[]) {
	    	ret = (byte[])value;
		} else {
			int pos = 0;
			for (byte[] block : (byte[][])value) {
		    	System.arraycopy(block, 0, ret, pos, block.length);
		    	pos += block.length;
			}
		}
    	return ret;
    }
    
    @Override
    public String asText() {
        return textValue();
    }

    @Override
    public boolean asBoolean(boolean defaultValue) {
    	String _value = textValue();
        if (_value != null) {
            if ("true".equals(_value.trim())) {
                return true;
            }
        }
        return defaultValue;
    }
    
    @Override
    public int asInt(int defaultValue) {
        return NumberInput.parseAsInt(textValue(), defaultValue);
    }

    @Override
    public long asLong(long defaultValue) {
        return NumberInput.parseAsLong(textValue(), defaultValue);
    }
    
    @Override
    public double asDouble(double defaultValue) {
        return NumberInput.parseAsDouble(textValue(), defaultValue);
    }
    
    public InputStream createInputStream() {
    	return new InputStream() {
			byte[] curBlock = null;
			int curBlockPos = -1;
			int posInCurBlock = -1;
			long globalPos = 0;
    		
			@Override
			public int read() throws IOException {
				if (!checkBlock())
					return -1;
				int ret = curBlock[curBlockPos];
				curBlockPos++;
				globalPos++;
				if (ret < 0)
					ret += 256;
				return ret;
			}
			
			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				if (!checkBlock())
					return -1;
				int ret = 0;
				while (len > 0) {
					int partLen = Math.min(curBlock.length - posInCurBlock, len);
					System.arraycopy(curBlock, posInCurBlock, b, off, partLen);
					posInCurBlock += partLen;
					globalPos += partLen;
					off += partLen;
					len -= partLen;
					ret += partLen;
					if (len > 0) {
						if (!checkBlock())
							break;
					}
				}
				return ret;
			}
			
			private boolean checkBlock() {
				if (curBlock == null || posInCurBlock >= curBlock.length) {
					if (value instanceof byte[]) {
						if (curBlockPos >= 0)
							return false;
						curBlockPos++;
						posInCurBlock = 0;
						curBlock = (byte[])value;
						return posInCurBlock < curBlock.length;
					} else {
						byte[][] blocks = (byte[][])value;
						if (curBlockPos + 1 >= blocks.length)
							return false;
						curBlockPos++;
						posInCurBlock = 0;
						curBlock = blocks[curBlockPos];
						return posInCurBlock < curBlock.length;
					}
				} else {
					return true;
				}
			}
			
			@Override
			public void close() throws IOException {
				System.out.println("JsonByteString input stream was closed: size=" + getByteLength() + ", globalPos=" + globalPos);
			}
		};
    }
    
    public Reader createReader() {
    	return new InputStreamReader(createInputStream(), charset);
    }
    
    @Override
    public final void serialize(JsonGenerator jg, SerializerProvider provider)
        throws IOException, JsonProcessingException {
        if (value == null) {
            jg.writeNull();
        } else if (jg instanceof KBaseJsonTreeGenerator) {
        	((KBaseJsonTreeGenerator)jg).writeJsonByteString(this);
        } else if (jg instanceof KBaseJsonGenerator) {
        	KBaseJsonGenerator kjg = (KBaseJsonGenerator)jg;
        	Reader r = createReader();
        	kjg.writeQuote(true);
        	while (true) {
        		int len = r.read(buf, 0, buf.length);
        		if (len < 0)
        			break;
        		if (len > 0)
        			kjg.writeStringNoQuotes(buf, 0, len);
        	}
        	kjg.writeQuote(false);
        } else {
        	jg.writeString(textValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (o.getClass() != getClass()) {
            return false;
        }
        return ((JsonByteString) o).textValue().equals(textValue());
    }
    
    @Override
    public int hashCode() { 
    	return textValue().hashCode(); 
    }

    @Override
    public String toString() {
    	String _value = textValue();
        int len = _value.length();
        len = len + 2 + (len >> 4);
        StringBuilder sb = new StringBuilder(len);
        appendQuoted(sb, _value);
        return sb.toString();
    }

    protected static void appendQuoted(StringBuilder sb, String content) {
        sb.append('"');
        CharTypes.appendQuoted(sb, content);
        sb.append('"');
    }
}
