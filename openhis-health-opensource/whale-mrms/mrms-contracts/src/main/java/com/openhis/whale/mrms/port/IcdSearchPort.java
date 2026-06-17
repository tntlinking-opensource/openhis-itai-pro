package com.openhis.whale.mrms.port;

import com.openhis.whale.mrms.port.model.IcdItemSnapshot;

import java.util.List;

public interface IcdSearchPort {

    List<IcdItemSnapshot> searchIcd10(String keyword, int limit);

    List<IcdItemSnapshot> searchIcd9Cm3(String keyword, int limit);
}
