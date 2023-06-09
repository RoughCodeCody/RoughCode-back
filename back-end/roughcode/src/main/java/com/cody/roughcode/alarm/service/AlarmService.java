package com.cody.roughcode.alarm.service;

import com.cody.roughcode.alarm.dto.req.AlarmReq;
import com.cody.roughcode.alarm.dto.res.AlarmRes;

import java.util.List;

public interface AlarmService {
    void insertAlarm(AlarmReq req);
    List<AlarmRes> getAlarmList(Long usersId);

    void deleteAlarm(String alarmId, Long usersId);
    void deleteAllAlarm(Long usersId);

    void deleteLimited();
}
