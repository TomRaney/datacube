package com.urbanairship.datacube.idservices;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.google.common.collect.Maps;
import com.urbanairship.datacube.Dimension;
import com.urbanairship.datacube.IdService;
import com.urbanairship.datacube.Util;
import com.urbanairship.datacube.dbharnesses.MapDbHarness.BoxedByteArray;

/**
 * An implementation of IdService that's backed by an in-memory map instead of a database.
 * This is useful for testing.
 */
public class MapIdService implements IdService {
    private static final Logger log = LogManager.getLogger(MapIdService.class);
    
    private final Map<Dimension<?>,Map<BoxedByteArray,Long>> idMap = Maps.newHashMap();
    private final Map<Dimension<?>,Long> nextIds = Maps.newHashMap();
    
    @Override
    public byte[] getId(Dimension<?> dimension, byte[] bytes) {
        if(!dimension.getDoIdSubstitution()) {
            throw new RuntimeException("Substitution is not enabled for the dimension " + 
                    dimension);
        }
            
        int numFieldBytes = dimension.getNumFieldBytes(); 
        
        if(numFieldBytes > 8) {
            throw new IllegalArgumentException("ID lengths > 8 are not supported. Do you " +
                    "really need more than 2^64 unique identifiers?");
        }

        Map<BoxedByteArray,Long> idMapForDimension = idMap.get(dimension);
        
        if(idMapForDimension == null) {
            // This is the first request for this dimension. Create a new map for the dimension.
            if(log.isDebugEnabled()) {
                log.debug("Creating new id map for dimension " + dimension);
            }
            idMapForDimension = Maps.newHashMap();
            idMap.put(dimension, idMapForDimension);
        }
        
        BoxedByteArray inputBytes = new BoxedByteArray(bytes);
        Long id = idMapForDimension.get(inputBytes);
        
        if(id == null) { 
            // We have never seen this input before. Assign it a new ID. 
            
            id = nextIds.get(dimension);
            
            if(id == null) {
                // We've never assigned an ID for this dimension+length. Start at 0.
                id = 0L;
            }

 
            // Remember this ID assignment, future requests should get the same ID
            idMapForDimension.put(inputBytes, id);
 
            // The next ID assigned for this dimension should be one greater than this one
            long nextId = id+1L;
            if(nextId == 0) { // IDs wrapped around and would reuse ID 0 if we didn't abort
                throw new RuntimeException("All unique IDs have been assigned!?!?");
            }
            nextIds.put(dimension, nextId);

        }
        
        byte[] idBytesNotTruncated = Util.longToBytes(id);
        byte[] idBytesTruncated = Arrays.copyOfRange(idBytesNotTruncated, 8-numFieldBytes, 8);
        assert Util.bytesToLongPad(idBytesNotTruncated) == Util.bytesToLongPad(idBytesTruncated);
        assert idBytesTruncated.length == numFieldBytes;
        
        if(log.isDebugEnabled()) {
            log.debug("Returning unique ID " + Hex.encodeHexString(idBytesTruncated) + 
                    " for dimension " + dimension + " input " + Hex.encodeHexString(bytes));
        }
        return idBytesTruncated;
    }
}
