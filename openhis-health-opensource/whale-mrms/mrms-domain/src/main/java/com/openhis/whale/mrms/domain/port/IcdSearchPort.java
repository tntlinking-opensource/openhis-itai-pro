package com.openhis.whale.mrms.domain.port;

import com.openhis.whale.mrms.domain.port.model.IcdItemSnapshot;

import java.util.List;

public interface IcdSearchPort {

    List<IcdItemSnapshot> searchIcd10(String keyword, int limit);

    List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit);
}
