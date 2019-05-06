package com.qcadoo.mes.orders.listeners;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.qcadoo.mes.orders.constants.OrdersConstants;
import com.qcadoo.mes.orders.constants.ScheduleFields;
import com.qcadoo.mes.orders.constants.SchedulePositionFields;
import com.qcadoo.mes.orders.constants.ScheduleSortOrder;
import com.qcadoo.mes.orders.constants.ScheduleWorkstationAssignCriterion;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchOrders;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;

@Service
public class ScheduleDetailsListeners {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    public void assignOperationsToWorkstations(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        Entity schedule = ((FormComponent) state).getEntity();
        Map<Long, Date> workstationsFinishDates = Maps.newHashMap();
        Set<Long> orderWithOperationWithoutWorkstations = Sets.newHashSet();
        List<Entity> positions = sortPositions(schedule.getId());
        for (Entity position : positions) {
            Long orderId = position.getBelongsToField(SchedulePositionFields.ORDER).getId();
            if (orderWithOperationWithoutWorkstations.contains(orderId)) {
                continue;
            }
            Integer machineWorkTime = position.getIntegerField(SchedulePositionFields.MACHINE_WORK_TIME);
            if (machineWorkTime == 0) {
                continue;
            }
            List<Entity> workstations = position.getBelongsToField(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT)
                    .getManyToManyField(TechnologyOperationComponentFields.WORKSTATIONS);
            if (workstations.isEmpty()) {
                orderWithOperationWithoutWorkstations.add(orderId);
                continue;
            }
            Map<Long, Date> operationWorkstationsFinishDates = Maps.newHashMap();
            for (Entity workstation : workstations) {
                Date finishDate = workstationsFinishDates.get(workstation.getId());
                if (finishDate == null) {
                    finishDate = schedule.getDateField(ScheduleFields.START_TIME);
                }
                Date newFinishDate = Date.from(finishDate.toInstant().plusSeconds(machineWorkTime));
                operationWorkstationsFinishDates.put(workstation.getId(), newFinishDate);
            }

            Entry<Long, Date> firstEntry;
            if (ScheduleWorkstationAssignCriterion.SHORTEST_TIME.getStringValue()
                    .equals(schedule.getStringField(ScheduleFields.WORKSTATION_ASSIGN_CRITERION))) {
                operationWorkstationsFinishDates = operationWorkstationsFinishDates.entrySet().stream().sorted(comparingByValue())
                        .collect(toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));
                firstEntry = operationWorkstationsFinishDates.entrySet().iterator().next();
            } else {
                if (workstationsFinishDates.isEmpty()) {
                    firstEntry = operationWorkstationsFinishDates.entrySet().iterator().next();
                } else {
                    firstEntry = operationWorkstationsFinishDates.entrySet().stream()
                            .filter(entry -> workstationsFinishDates.containsKey(entry.getKey())).findFirst()
                            .orElse(operationWorkstationsFinishDates.entrySet().iterator().next());
                }
            }
            updatePositionWorkstationAndDates(firstEntry, workstationsFinishDates, position, workstations);
        }
    }

    private void updatePositionWorkstationAndDates(Entry<Long, Date> firstEntry, Map<Long, Date> workstationsFinishDates,
            Entity position, List<Entity> workstations) {
        workstationsFinishDates.put(firstEntry.getKey(), firstEntry.getValue());
        position.setField(SchedulePositionFields.WORKSTATION,
                workstations.stream().filter(entity -> entity.getId().equals(firstEntry.getKey())).findFirst().get());
        position.setField(SchedulePositionFields.START_TIME, Date.from(firstEntry.getValue().toInstant()
                .minusSeconds(position.getIntegerField(SchedulePositionFields.MACHINE_WORK_TIME))));
        position.setField(SchedulePositionFields.END_TIME, firstEntry.getValue());
        position.getDataDefinition().save(position);
    }

    private List<Entity> sortPositions(Long scheduleId) {
        Entity schedule = dataDefinitionService.get(OrdersConstants.PLUGIN_IDENTIFIER, OrdersConstants.MODEL_SCHEDULE)
                .get(scheduleId);
        if (ScheduleSortOrder.DESCENDING.getStringValue().equals(schedule.getStringField(ScheduleFields.SORT_ORDER))) {
            return schedule.getHasManyField(ScheduleFields.POSITIONS).find()
                    .createAlias(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT,
                            SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT, JoinType.INNER)
                    .addOrder(SearchOrders.desc(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT + "."
                            + TechnologyOperationComponentFields.NODE_NUMBER))
                    .addOrder(SearchOrders.desc(SchedulePositionFields.MACHINE_WORK_TIME)).list().getEntities();
        } else {
            return schedule.getHasManyField(ScheduleFields.POSITIONS).find()
                    .createAlias(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT,
                            SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT, JoinType.INNER)
                    .addOrder(SearchOrders.desc(SchedulePositionFields.TECHNOLOGY_OPERATION_COMPONENT + "."
                            + TechnologyOperationComponentFields.NODE_NUMBER))
                    .addOrder(SearchOrders.asc(SchedulePositionFields.MACHINE_WORK_TIME)).list().getEntities();
        }
    }

    public void assignWorkersToOperations(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        throw new UnsupportedOperationException();
    }
}
