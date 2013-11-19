package us.kbase.common.service;

import java.io.IOException;
import java.nio.charset.Charset;

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
    final static int INT_SPACE = ' ';

    final static JsonByteString EMPTY_STRING_NODE = new JsonByteString("");
    final static Charset charset = Charset.forName("UTF-8");

    // Correction that helps to pack text scalars into byte array made by rsutormin
    private final byte[] value; 

    public JsonByteString(String v) { 
    	if (v.length() > 0) {
    	    // Correction that helps to pack text scalars into byte array made by rsutormin
    		value = v.getBytes(charset); 
    	} else {
    		value = null;
    	}
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
    	if (value == null)
    		return "";
        // Correction that helps to pack text scalars into byte array made by rsutormin
        return new String(value, charset);
    }

    @Override
    public byte[] binaryValue() throws IOException {
        // Correction that helps to pack text scalars into byte array made by rsutormin
    	if (value == null)
    		return new byte[0];
    	byte[] ret = new byte[value.length];
    	System.arraycopy(value, 0, ret, 0, ret.length);
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
    
    @Override
    public final void serialize(JsonGenerator jg, SerializerProvider provider)
        throws IOException, JsonProcessingException {
    	String _value = textValue();
        if (_value == null) {
            jg.writeNull();
        } else {
            jg.writeString(_value);
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
