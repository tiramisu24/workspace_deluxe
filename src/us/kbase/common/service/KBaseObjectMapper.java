package us.kbase.common.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerFactory;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.DeserializerFactory;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * This extension of jackson ObjectMapper is set up so that it allows to deal with:
 * (1) Tuples, (2) UObjects, (3) pack text scalars as byte arrays in text json nodes,
 * (4) sort keys in tree nodes of maps and POJOs. 
 * @author rsutormin
 */
@SuppressWarnings("serial")
public class KBaseObjectMapper extends ObjectMapper {
	public KBaseObjectMapper() {
		super(new KBaseJsonFactory(), null, new DefaultDeserializationContext.Impl(
				new KBaseBeanDeserializerFactory(new DeserializerFactoryConfig())));
		getFactory().setCodec(this);
		registerModule(new JacksonTupleModule());
	}
	
	public void sortKeysInParsingTree(boolean sort) {
		configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, sort);
	}
	
	@Override
	@SuppressWarnings("unchecked")
    public <T extends JsonNode> T valueToTree(Object fromValue)
    		throws IllegalArgumentException {
		KBaseJsonTreeGenerator gen = new KBaseJsonTreeGenerator(this);
        //if (fromValue == null) return null;
        //TokenBuffer buf = new TokenBuffer(this);
        JsonNode result;
        try {
            writeValue(gen, fromValue);
            result = gen.getTree();
        } catch (IOException e) { // should not occur, no real i/o...
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return (T) result;
    } 
	
	public static class KBaseJsonFactory extends MappingJsonFactory {
		@Override
	    protected JsonParser _createParser(InputStream in, IOContext ctxt)
	    		throws IOException, JsonParseException {
			return new KBaseJsonParser(this, in);
		}
		
		@Override
		protected JsonGenerator _createUTF8Generator(OutputStream out,
				IOContext ctxt) throws IOException {
	        KBaseJsonGenerator gen = new KBaseJsonGenerator(ctxt,
	                _generatorFeatures, _objectCodec, out);
	        if (_characterEscapes != null) {
	            gen.setCharacterEscapes(_characterEscapes);
	        }
	        SerializableString rootSep = _rootValueSeparator;
	        if (rootSep != DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR) {
	            gen.setRootValueSeparator(rootSep);
	        }
	        return gen;
		}
	}
	
	/**
	 * This class override method createTreeDeserializer from standard json deserializer
	 * factory that allows us to substitute standard json node deserializer by our
	 * CustomJsonNodeDeserializer (see below). We can not just register this binding
	 * through JacksonTupleModule as we do for Tuples and UObjects because standard
	 * json code is trying to find this deserializer by JavaType instead of by Class.
	 */
	public static class KBaseBeanDeserializerFactory extends BeanDeserializerFactory {
		public KBaseBeanDeserializerFactory(DeserializerFactoryConfig cfg) {
			super(cfg);
		}
		@Override
		public TypeDeserializer findTypeDeserializer(
				DeserializationConfig config, JavaType baseType)
				throws JsonMappingException {
			return super.findTypeDeserializer(config, baseType);
		}
	    @Override
	    public DeserializerFactory withConfig(DeserializerFactoryConfig config) {
	        if (_factoryConfig == config) {
	            return this;
	        }
	        return new KBaseBeanDeserializerFactory(config);
	    }

	    @Override
	    public JsonDeserializer<?> createTreeDeserializer(DeserializationConfig config,
	            JavaType nodeType, BeanDescription beanDesc) throws JsonMappingException {
	        @SuppressWarnings("unchecked")
	        Class<? extends JsonNode> nodeClass = (Class<? extends JsonNode>) nodeType.getRawClass();
	    	return CustomJsonNodeDeserializer.getDeserializer(nodeClass);
	    }
	}
	
	/**
	 * This is a copy of com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer with 
	 * some corrections that help (1) to create special JsonByteString nodes for text scalars
	 * and (2) to sort keys in tree nodes of maps and POJOs (which are of the same node type).
	 */
	public static class CustomJsonNodeDeserializer extends BaseNodeDeserializer {
	    private final static CustomJsonNodeDeserializer instance = new CustomJsonNodeDeserializer();

	    protected CustomJsonNodeDeserializer() {
	    }

	    public static JsonDeserializer<? extends JsonNode> getDeserializer(Class<?> nodeClass) {
	        if (nodeClass == ObjectNode.class) {
	            return ObjectDeserializer.getInstance();
	        }
	        if (nodeClass == ArrayNode.class) {
	            return ArrayDeserializer.getInstance();
	        }
	        return instance;
	    }
	    
	    @Override
	    public JsonNode deserialize(JsonParser jp, DeserializationContext ctxt)
	        throws IOException, JsonProcessingException {
	        switch (jp.getCurrentToken()) {
	        case START_OBJECT:
	            return deserializeObject(jp, ctxt, ctxt.getNodeFactory());
	        case START_ARRAY:
	            return deserializeArray(jp, ctxt, ctxt.getNodeFactory());
	        default:
	            return deserializeAny(jp, ctxt, ctxt.getNodeFactory());
	        }
	    }

	    final static class ObjectDeserializer extends BaseNodeDeserializer {
	        private static final long serialVersionUID = 1L;

	        protected final static ObjectDeserializer _instance = new ObjectDeserializer();

	        protected ObjectDeserializer() { }

	        public static ObjectDeserializer getInstance() { return _instance; }
	        
	        @Override
	        public ObjectNode deserialize(JsonParser jp, DeserializationContext ctxt)
	            throws IOException, JsonProcessingException
	        {
	            if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
	                jp.nextToken();
	                return deserializeObject(jp, ctxt, ctxt.getNodeFactory());
	            }
	            if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
	                return deserializeObject(jp, ctxt, ctxt.getNodeFactory());
	            }
	            throw ctxt.mappingException(ObjectNode.class);
	         }
	    }
	        
	    final static class ArrayDeserializer extends BaseNodeDeserializer {
	        private static final long serialVersionUID = 1L;

	        protected final static ArrayDeserializer _instance = new ArrayDeserializer();

	        protected ArrayDeserializer() { }

	        public static ArrayDeserializer getInstance() { return _instance; }
	        
	        @Override
	        public ArrayNode deserialize(JsonParser jp, DeserializationContext ctxt)
	            throws IOException, JsonProcessingException
	        {
	            if (jp.isExpectedStartArrayToken()) {
	                return deserializeArray(jp, ctxt, ctxt.getNodeFactory());
	            }
	            throw ctxt.mappingException(ArrayNode.class);
	        }
	    }
	}

	/**
	 * This is a copy of internal class BaseNodeDeserializer inside 
	 * com.fasterxml.jackson.databind.deser.std.JsonNodeDeserializer with 
	 * some corrections that help (1) to create special JsonByteString nodes for text scalars
	 * and (2) to sort keys in tree nodes of maps and POJOs (which are of the same node type).
	 */
	public static abstract class BaseNodeDeserializer extends StdDeserializer<JsonNode> {
	    public BaseNodeDeserializer() {
	        super(JsonNode.class);
	    }
	    
	    @Override
	    public Object deserializeWithType(JsonParser jp, DeserializationContext ctxt,
	            TypeDeserializer typeDeserializer)
	        throws IOException, JsonProcessingException
	    {
	        return typeDeserializer.deserializeTypedFromAny(jp, ctxt);
	    }

	    @Override
	    public JsonNode getNullValue() {
	        return NullNode.getInstance();
	    }

	    protected void _reportProblem(JsonParser jp, String msg)
	        throws JsonMappingException {
	        throw new JsonMappingException(msg, jp.getTokenLocation());
	    }
	    
	    protected final ObjectNode deserializeObject(JsonParser jp, DeserializationContext ctxt,
	            final JsonNodeFactory nodeFactory)            
	        throws IOException, JsonProcessingException {
	        ObjectNode node = nodeFactory.objectNode();
	        JsonToken t = jp.getCurrentToken();
	        if (t == JsonToken.START_OBJECT) {
	            t = jp.nextToken();
	        }
            // Correction for sorting keys made by rsutormin
	        boolean sortKeys = false;
	        if (jp.getCodec() instanceof ObjectMapper) {
	        	ObjectMapper m = (ObjectMapper)jp.getCodec();
	        	sortKeys = m.getSerializationConfig().isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
	        }
	        Map<String, JsonNode> tempMap = sortKeys ? new TreeMap<String, JsonNode>() :
	        	new LinkedHashMap<String, JsonNode>();
	        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
	            String fieldName = jp.getCurrentName();
	            JsonNode value;
	            switch (jp.nextToken()) {
	            case START_OBJECT:
	                value = deserializeObject(jp, ctxt, nodeFactory);
	                break;
	            case START_ARRAY:
	                value = deserializeArray(jp, ctxt, nodeFactory);
	                break;
			    // Correction for packing strings into byte array made by rsutormin
	            case VALUE_STRING:
	                value = nodeFactorytextNode(jp);
	                break;
	            default:
	                value = deserializeAny(jp, ctxt, nodeFactory);
	            }
	            // Correction for sorting keys made by rsutormin
	            tempMap.put(fieldName, value);
	        }
            // Correction for sorting keys made by rsutormin
	        for (Map.Entry<String, JsonNode> entry : tempMap.entrySet())
	        	node.replace(entry.getKey(), entry.getValue());
	        return node;
	    }
	    
	    protected final ArrayNode deserializeArray(JsonParser jp, DeserializationContext ctxt,
	            final JsonNodeFactory nodeFactory)            
	        throws IOException, JsonProcessingException
	    {
	        ArrayNode node = nodeFactory.arrayNode();
	        while (true) {
	            JsonToken t = jp.nextToken();
	            if (t == null) {
	                throw ctxt.mappingException("Unexpected end-of-input when binding data into ArrayNode");
	            }
	            switch (t) {
	            case START_OBJECT:
	                node.add(deserializeObject(jp, ctxt, nodeFactory));
	                break;
	            case START_ARRAY:
	                node.add(deserializeArray(jp, ctxt, nodeFactory));
	                break;
	            case END_ARRAY:
	                return node;
		        // Correction for packing strings into byte array made by rsutormin
	            case VALUE_STRING:
	                node.add(nodeFactorytextNode(jp));
	                break;
	            default:
	                node.add(deserializeAny(jp, ctxt, nodeFactory));
	                break;
	            }
	        }
	    }

        // Correction for packing strings into byte array made by rsutormin
	    private JsonByteString nodeFactorytextNode(JsonParser jp) throws JsonParseException, IOException {
	    	if (jp.getCurrentToken() == JsonToken.VALUE_STRING && jp instanceof KBaseJsonParser) {
	    		KBaseJsonParser kjp = (KBaseJsonParser)jp;
	    		JsonByteString ret = new JsonByteString();
	    		Writer w = ret.createWriter();
	    		kjp.writeTextIntoWriter(w);
	    		w.close();
	    		return ret;
	    	} else {
	    		String text = jp.getText();
	    		return JsonByteString.valueOf(text);
	    	}
	    }

	    protected final JsonNode deserializeAny(JsonParser jp, DeserializationContext ctxt,
	            final JsonNodeFactory nodeFactory)            
	        throws IOException, JsonProcessingException
	    {
	        switch (jp.getCurrentToken()) {
	        case START_OBJECT:
	            return deserializeObject(jp, ctxt, nodeFactory);

	        case START_ARRAY:
	            return deserializeArray(jp, ctxt, nodeFactory);

	        case FIELD_NAME:
	            return deserializeObject(jp, ctxt, nodeFactory);

	        case VALUE_EMBEDDED_OBJECT:
	            {
	                Object ob = jp.getEmbeddedObject();
	                if (ob == null) { // should this occur?
	                    return nodeFactory.nullNode();
	                }
	                Class<?> type = ob.getClass();
	                if (type == byte[].class) { // most common special case
	                    return nodeFactory.binaryNode((byte[]) ob);
	                }
	                // any other special handling needed?
	                return nodeFactory.pojoNode(ob);
	            }

		    // Correction for packing strings into byte array made by rsutormin
	        case VALUE_STRING:
	            return nodeFactorytextNode(jp);

	        case VALUE_NUMBER_INT:
	            {
	                JsonParser.NumberType nt = jp.getNumberType();
	                if (nt == JsonParser.NumberType.BIG_INTEGER
	                    || ctxt.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
	                    return nodeFactory.numberNode(jp.getBigIntegerValue());
	                }
	                if (nt == JsonParser.NumberType.INT) {
	                    return nodeFactory.numberNode(jp.getIntValue());
	                }
	                return nodeFactory.numberNode(jp.getLongValue());
	            }

	        case VALUE_NUMBER_FLOAT:
	            {
	                JsonParser.NumberType nt = jp.getNumberType();
	                if (nt == JsonParser.NumberType.BIG_DECIMAL
	                    || ctxt.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
	                    return nodeFactory.numberNode(jp.getDecimalValue());
	                }
	                return nodeFactory.numberNode(jp.getDoubleValue());
	            }

	        case VALUE_TRUE:
	            return nodeFactory.booleanNode(true);

	        case VALUE_FALSE:
	            return nodeFactory.booleanNode(false);

	        case VALUE_NULL:
	            return nodeFactory.nullNode();
	            
	            // These states can not be mapped; input stream is
	            // off by an event or two

	        //case END_OBJECT:
	        //case END_ARRAY:
	        default:
	            throw ctxt.mappingException(getValueClass());
	        }
	    }
	}
}
