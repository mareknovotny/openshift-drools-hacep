package org.kie.hacep;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.Test;
import org.kie.hacep.core.Bootstrap;
import org.kie.hacep.core.infra.election.State;
import org.kie.hacep.message.ControlMessage;
import org.kie.hacep.sample.kjar.StockTickEvent;
import org.kie.remote.RemoteFactHandle;
import org.kie.remote.RemoteKieSession;
import org.kie.remote.command.FireUntilHaltCommand;
import org.kie.remote.command.InsertCommand;
import org.kie.remote.command.RemoteCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;
import static org.kie.remote.util.SerializationUtil.deserialize;

public class PodAsReplicaTest extends KafkaFullTopicsTests {

    private Logger logger = LoggerFactory.getLogger(PodAsReplicaTest.class);

    @Test(timeout = 60000L)
    public void processOneSentMessageAsLeaderAndThenReplicaTest() {
        Bootstrap.startEngine(envConfig);
        Bootstrap.getConsumerController().getCallback().updateStatus(State.LEADER);
        KafkaConsumer eventsConsumer = kafkaServerTest.getConsumer(envConfig.getEventsTopicName(),
                                                                   Config.getConsumerConfig("eventsConsumerProcessOneSentMessageAsLeaderTest"));
        KafkaConsumer controlConsumer = kafkaServerTest.getConsumer(envConfig.getControlTopicName(),
                                                                    Config.getConsumerConfig("controlConsumerProcessOneSentMessageAsLeaderTest"));

        KafkaConsumer<byte[], String> kafkaLogConsumer = kafkaServerTest.getStringConsumer(TEST_KAFKA_LOGGER_TOPIC);
        kafkaServerTest.insertBatchStockTicketEvent(1,
                                                    topicsConfig,
                                                    RemoteKieSession.class);
        try {

            //EVENTS TOPIC
            ConsumerRecords eventsRecords = eventsConsumer.poll(5000);
            assertEquals(2, eventsRecords.count());
            Iterator<ConsumerRecord<String, byte[]>> eventsRecordIterator = eventsRecords.iterator();

            ConsumerRecord<String, byte[]> eventsRecord = eventsRecordIterator.next();
            assertEquals(eventsRecord.topic(), envConfig.getEventsTopicName());
            RemoteCommand remoteCommand = deserialize(eventsRecord.value());
            assertEquals(eventsRecord.offset(), 0);
            assertNotNull(remoteCommand.getId());
            assertTrue( remoteCommand instanceof FireUntilHaltCommand );

            eventsRecord = eventsRecordIterator.next();
            assertEquals(eventsRecord.topic(), envConfig.getEventsTopicName());
            remoteCommand = deserialize(eventsRecord.value());
            assertEquals(eventsRecord.offset(), 1);
            assertNotNull(remoteCommand.getId());
            InsertCommand insertCommand = (InsertCommand) remoteCommand;
            assertEquals(insertCommand.getEntryPoint(), "DEFAULT");
            assertNotNull(insertCommand.getId());
            assertNotNull(insertCommand.getFactHandle());
            RemoteFactHandle remoteFactHandle = insertCommand.getFactHandle();
            StockTickEvent eventsTicket = (StockTickEvent) remoteFactHandle.getObject();
            assertEquals(eventsTicket.getCompany(), "RHT");

            //CONTROL TOPIC
            ConsumerRecords controlRecords = waitForControlMessage( controlConsumer );

            Iterator<ConsumerRecord<String, byte[]>> controlRecordIterator = controlRecords.iterator();
            checkFireSideEffects( controlRecordIterator.next() );

            if (controlRecords.count() == 2) {
                checkInsertSideEffects( eventsRecord, controlRecordIterator.next() );
            } else {
                // wait for second control message
                controlRecords = waitForControlMessage( controlConsumer );
                checkInsertSideEffects( eventsRecord, (ConsumerRecord<String, byte[]>) controlRecords.iterator().next() );
            }

            //no more msg to consume as a leader
            eventsRecords = eventsConsumer.poll(5000);
            assertEquals(0, eventsRecords.count());
            controlRecords = controlConsumer.poll(5000);
            assertEquals(0, controlRecords.count());

            // SWITCH AS a REPLICA
            Bootstrap.getConsumerController().getCallback().updateStatus(State.REPLICA);

            ConsumerRecords<byte[], String> recordsLog = kafkaLogConsumer.poll(20000);
            Iterator<ConsumerRecord<byte[], String>> recordIterator = recordsLog.iterator();
            List<String> kafkaLoggerMsgs = new ArrayList();
            while (recordIterator.hasNext()) {
                ConsumerRecord<byte[], String> record = recordIterator.next();
                kafkaLoggerMsgs.add(record.value());
            }

            String sideEffectOnLeader = null;
            String sideEffectOnReplica = null;
            for (String item : kafkaLoggerMsgs) {
                if (item.startsWith("sideEffectOn")) {
                    if (item.endsWith(":null")) {
                        fail("SideEffects null");
                    }
                    if(item.startsWith("sideEffectOnReplica:")){
                        sideEffectOnReplica = item.substring(item.indexOf("["));
                    }
                    if(item.startsWith("sideEffectOnLeader:")){
                        sideEffectOnLeader = item.substring(item.indexOf("["));
                    }
                }
            }
            assertNotNull(sideEffectOnLeader);
            assertNotNull(sideEffectOnReplica);

            assertEquals(sideEffectOnLeader, sideEffectOnReplica);
        } catch (Exception ex) {
            logger.error(ex.getMessage(),
                         ex);
        } finally {
            eventsConsumer.close();
            controlConsumer.close();
            kafkaLogConsumer.close();
        }
    }

    private ConsumerRecords waitForControlMessage( KafkaConsumer controlConsumer ) throws InterruptedException {
        ConsumerRecords controlRecords = controlConsumer.poll(5000);
        while (controlRecords.count() == 0) {
            Thread.sleep( 10 );
            controlRecords = controlConsumer.poll( 5000 );
        }
        return controlRecords;
    }

    private void checkFireSideEffects( ConsumerRecord<String, byte[]> controlRecord ) {
        // FireUntilHalt command has no side effects
        assertEquals(controlRecord.topic(), envConfig.getControlTopicName());
        ControlMessage controlMessage = deserialize(controlRecord.value());
        assertEquals(controlRecord.offset(), 0);
        assertTrue(controlMessage.getSideEffects().isEmpty());
    }

    private void checkInsertSideEffects( ConsumerRecord<String, byte[]> eventsRecord, ConsumerRecord<String, byte[]> controlRecord ) {
        assertEquals(controlRecord.topic(), envConfig.getControlTopicName());
        ControlMessage controlMessage = deserialize(controlRecord.value());
        assertEquals(controlRecord.offset(), 1);
        assertTrue(!controlMessage.getSideEffects().isEmpty());
        assertTrue(controlMessage.getSideEffects().size() == 1);
        String sideEffect = controlMessage.getSideEffects().iterator().next().toString();
        //Same msg content on Events topic and control topics
        assertEquals(controlRecord.key(), eventsRecord.key());
    }
}
