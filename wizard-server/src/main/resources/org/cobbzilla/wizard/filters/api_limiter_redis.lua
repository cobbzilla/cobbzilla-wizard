local len = #ARGV  --ARGV is the argument array containing triples (limit, interval, block), # returns length
local ret = false --lua false translates to redis null
for i=1,len,3 do    --iterate over arguments from 1 to len, step 3
    local fullkey = KEYS[1] .. ':' .. ARGV[i] .. ':' .. ARGV[i+1] .. ':' .. ARGV[i+2]
    --KEYS is the array of keys, .. concatenates strings
    local bucket = redis.call('GET', fullkey)   -- return false if not found
    if ( (bucket ~= false) and ( tonumber(bucket) >= tonumber(ARGV[i])) ) then
        ret = i  -- set last limit breach
    else
        local count = redis.call('INCR', fullkey) --increments the key, sets it to 1 if it does not exist
        if tonumber(count) >= tonumber(ARGV[i]) then
            redis.call('PEXPIRE', fullkey, tonumber(ARGV[i+2]))
            ret = i --if the limit is breached, update the key to expire in block miliseconds, and set last limit breach
        else
            if count == 1 then
              redis.call('PEXPIRE', fullkey, tonumber(ARGV[i+1]))
                -- if just created the key, set expiry in interval miliseconds
            end
        end
    end
end

if not(ret == nil) and type(ret) == "number" then
    return (ret - 1) / 3
end
return ret
