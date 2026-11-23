package com.netbanking.setup;

import com.netbanking.model.CustomerProfile;
import java.util.Locale;

final class SampleContact {
    private SampleContact() {}

    static CustomerProfile profile(String name, int fixtureNumber) {
        String phone = String.format(Locale.ROOT, "+1 202 555 %04d", 99 + fixtureNumber);
        return new CustomerProfile(name, phone, "Flat " + (10 + fixtureNumber) + ", Demo Residency",
                "Pune", "Maharashtra", "411001");
    }
}
