package org.cobbzilla.wizard.validation;

import org.cobbzilla.util.dns.DnsRecord;
import org.cobbzilla.util.string.ValidationRegexes;

import java.util.ArrayList;
import java.util.List;

public class DnsRecordValidator {

    public static List<ConstraintViolationBean> validate (DnsRecord dnsRecord) {

        final List<ConstraintViolationBean> errors = new ArrayList<>();

        if (!ValidationRegexes.HOST_PATTERN.matcher(dnsRecord.getFqdn()).matches()) {
            errors.add(new ConstraintViolationBean("err.dnsRecord.fqdn.invalid"));
        }

        /* todo: the "value" field is not always an IP address, depends on record type
        if (!ValidationRegexes.IPv4_PATTERN.matcher(dnsRecord.getValue()).matches()
                && !ValidationRegexes.IPv6_PATTERN.matcher(dnsRecord.getValue()).matches()) {
            errors.add(new ConstraintViolationBean("err.dnsRecord.ip.invalid"));
        }
        */

        if (dnsRecord.getTtl() <= 0) {
            errors.add(new ConstraintViolationBean("err.dnsRecord.ttl.invalid"));
        }

        if (!dnsRecord.hasAllRequiredOptions()) {
            errors.add(new ConstraintViolationBean("err.dnsRecord.options.invalid"));
        }

        return errors;
    }

}
