package io.galeb.manager.scheduler.tasks;

import static io.galeb.manager.scheduler.SchedulerConfiguration.GALEB_DISABLE_SCHED;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.galeb.manager.common.EmptyStream;
import io.galeb.manager.common.Properties;
import io.galeb.manager.engine.Driver;
import io.galeb.manager.engine.Driver.StatusFarm;
import io.galeb.manager.engine.DriverBuilder;
import io.galeb.manager.engine.farm.FarmEngine;
import io.galeb.manager.entity.AbstractEntity;
import io.galeb.manager.entity.AbstractEntity.EntityStatus;
import io.galeb.manager.entity.Farm;
import io.galeb.manager.entity.Rule;
import io.galeb.manager.entity.Target;
import io.galeb.manager.entity.VirtualHost;
import io.galeb.manager.jms.JmsConfiguration;
import io.galeb.manager.repository.FarmRepository;
import io.galeb.manager.repository.RuleRepository;
import io.galeb.manager.repository.TargetRepository;
import io.galeb.manager.repository.VirtualHostRepository;
import io.galeb.manager.security.CurrentUser;
import io.galeb.manager.security.SystemUserService;

@Component
public class CheckFarms {

    private static final Log LOGGER = LogFactory.getLog(CheckFarms.class);

    @Autowired
    FarmRepository farmRepository;

    @Autowired
    VirtualHostRepository virtualHostRepository;

    @Autowired
    RuleRepository ruleRepository;

    @Autowired
    TargetRepository targetRepository;

    @Autowired
    JmsTemplate jms;

    private final ObjectMapper mapper = new ObjectMapper();


    private boolean disableJms = Boolean.getBoolean(System.getProperty(
                                    JmsConfiguration.DISABLE_JMS, Boolean.toString(false)));

    private Properties getProperties(final Farm farm,
                                     final AbstractEntity<?> entity,
                                     String path,
                                     long numElements) {
        return getProperties(farm,
                             entity,
                             path,
                             numElements,
                             EmptyStream.get());
    }

    private Properties getProperties(final Farm farm,
                                     final AbstractEntity<?> entity,
                                     String path,
                                     long numElements,
                                     final Stream<? extends AbstractEntity<?>> parents) {
        Properties properties = new Properties();
        properties.put("api", farm.getApi());
        properties.put("name", entity.getName());
        properties.put("path", path);
        properties.put("id", entity.getId());
        properties.put("numElements", numElements);
        properties.put("parents", parents);
        return properties;
    }

    @Scheduled(fixedRate = 10000)
    private void run() {

        String disableSchedulerStr = System.getenv(GALEB_DISABLE_SCHED);
        if (disableSchedulerStr != null && Boolean.parseBoolean(disableSchedulerStr)) {
            LOGGER.warn(GALEB_DISABLE_SCHED+" is true");
            return;
        }

        LOGGER.debug("TASK checkFarm executing");

        Authentication currentUser = CurrentUser.getCurrentAuth();
        SystemUserService.runAs();
        try {
            StreamSupport.stream(farmRepository.findAll().spliterator(), false)
                                    .filter(farm -> !farm.getStatus().equals(EntityStatus.DISABLED))
                                    .forEach(farm -> {

                final Driver driver = DriverBuilder.getDriver(farm);
                AtomicBoolean isOk = new AtomicBoolean(true);

                if (!farm.getStatus().equals(EntityStatus.ERROR)) {

                   final long virtualhostCount = getVirtualhosts(farm).count();

                   getVirtualhosts(farm).forEach(virtualhost -> {
                        Properties properties = getProperties(farm,
                                                              virtualhost,
                                                              "virtualhost",
                                                              virtualhostCount);
                        boolean lastStatus = isOk.get();
                        isOk.set(driver.status(properties).equals(StatusFarm.OK) && lastStatus);
                    });

                    final long ruleCount = getRules(farm).count();

                    getRules(farm).forEach(rule -> {
                        Stream<VirtualHost> virtualhosts =  StreamSupport.stream(rule.getParents().spliterator(), false);
                        Properties properties = getProperties(farm,
                                                              rule,
                                                              "rule",
                                                              ruleCount,
                                                              virtualhosts);
                        boolean lastStatus = isOk.get();
                        isOk.set(driver.status(properties).equals(StatusFarm.OK) && lastStatus);
                    });

                    Map<String, Long> targetsCountMap = getTargets(farm).parallel().collect(
                                    Collectors.groupingBy(target -> target.getTargetType().getName(),
                                                          Collectors.counting()));

                    getTargets(farm).forEach(target -> {
                        String targetTypeName = target.getTargetType().getName();
                        Properties properties = getProperties(farm,
                                                              target,
                                                              target.getTargetType().getName().toLowerCase(),
                                                              targetsCountMap.get(targetTypeName));
                        boolean lastStatus = isOk.get();
                        isOk.set(driver.status(properties).equals(StatusFarm.OK) && lastStatus);
                    });

                } else {
                    isOk.set(false);
                }

                farm.setStatus(isOk.get() ? EntityStatus.OK : EntityStatus.ERROR);
                if (!isOk.get()) {
                    if (farm.isAutoReload() && !disableJms) {
                        jms.convertAndSend(FarmEngine.QUEUE_RELOAD, farm);
                    } else {
                        LOGGER.warn("FARM STATUS FAIL (But AutoReload disabled): "+farm.getName()+" ["+farm.getApi()+"]");
                    }
                } else {
                    LOGGER.info("FARM STATUS OK: "+farm.getName()+" ["+farm.getApi()+"]");
                }
            });
        } catch (Exception e) {
            LOGGER.error(e);
        } finally {
            SystemUserService.runAs(currentUser);
            LOGGER.debug("TASK checkFarm finished");
        }
    }

    private Stream<Target> getTargets(Farm farm) {
        return StreamSupport.stream(
                targetRepository.findByFarmId(farm.getId()).spliterator(), false);
    }

    private Stream<Rule> getRules(Farm farm) {
        return StreamSupport.stream(
                ruleRepository.findByFarmId(farm.getId()).spliterator(), false)
                .filter(rule -> !rule.getParents().isEmpty());
    }

    private Stream<VirtualHost> getVirtualhosts(Farm farm) {
        return StreamSupport.stream(
                virtualHostRepository.findByFarmId(farm.getId()).spliterator(), false);
    }

    @Scheduled(fixedRate = 10000)
    private void diff() {
        Authentication currentUser = CurrentUser.getCurrentAuth();
        SystemUserService.runAs();

        StreamSupport.stream(farmRepository.findAll().spliterator(), false)
                     .filter(farm -> !farm.getStatus().equals(EntityStatus.DISABLED))
                     .forEach(farm ->
        {
            final Driver driver = DriverBuilder.getDriver(farm);
            Map<String, Object> properties = new HashMap<>();
            properties.put("api", farm.getApi());
            properties.put("virtualhosts", getVirtualhosts(farm).collect(Collectors.toSet()));
            properties.put("backendpools", getTargets(farm)
                    .filter(target -> target.getTargetType().getName().equals("BackendPool"))
                    .collect(Collectors.toSet()));
            properties.put("backends", getTargets(farm)
                    .filter(target -> target.getTargetType().getName().equals("Backend"))
                    .collect(Collectors.toSet()));
            properties.put("rules", getRules(farm).collect(Collectors.toSet()));

            try {
                String json = mapper.writeValueAsString(driver.diff(properties));
                LOGGER.warn("----------------------");
                LOGGER.warn(json);
                LOGGER.warn("----------------------");
            } catch (Exception e) {
                LOGGER.error(e);
                e.printStackTrace();
            }

        });

        SystemUserService.runAs(currentUser);
    }

}
