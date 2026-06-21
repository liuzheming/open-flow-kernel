CREATE TABLE IF NOT EXISTS process_def (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_code VARCHAR(128) NOT NULL DEFAULT '',
    proc_name VARCHAR(64) NOT NULL DEFAULT '',
    biz_proc_code VARCHAR(128) NOT NULL DEFAULT '',
    version VARCHAR(31) NOT NULL DEFAULT '',
    proc_type VARCHAR(32) NOT NULL DEFAULT '',
    proc_type_name VARCHAR(64) NOT NULL DEFAULT '',
    business_code VARCHAR(31) NOT NULL DEFAULT '',
    data_packet_resolver_class VARCHAR(1024) NOT NULL DEFAULT '',
    pre_check_config TEXT,
    is_delete TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    packet_init_config TEXT,
    init_inst_data VARCHAR(255) NOT NULL DEFAULT '',
    init_params VARCHAR(256) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY uniq_process_def_code (proc_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_task_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    process_def_id BIGINT NOT NULL DEFAULT 0,
    process_code VARCHAR(255) NOT NULL DEFAULT '',
    task_code VARCHAR(64) NOT NULL DEFAULT '',
    `condition` VARCHAR(255) NOT NULL DEFAULT '',
    `key` VARCHAR(32) NOT NULL DEFAULT '',
    `value` LONGTEXT NOT NULL,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_task_config_proc (process_def_id, task_code),
    KEY idx_process_task_config_process_code (process_code, task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_def_id BIGINT NOT NULL DEFAULT 0,
    proc_name VARCHAR(64) NOT NULL DEFAULT '',
    business_code VARCHAR(31) NOT NULL DEFAULT '',
    city_operate_mode VARCHAR(31) NOT NULL DEFAULT 'INNER_DIRECT',
    relate_proc_inst_id BIGINT NOT NULL DEFAULT 0,
    relate_task_inst_id BIGINT NOT NULL DEFAULT 0,
    main_proc_inst_id BIGINT NOT NULL DEFAULT 0,
    parent_proc_inst_id BIGINT NOT NULL DEFAULT 0,
    derived_proc_inst_id BIGINT NOT NULL DEFAULT 0,
    process_candidates VARCHAR(256) NOT NULL DEFAULT '',
    cancel_task_name VARCHAR(20) NOT NULL DEFAULT '',
    cancel_code INT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(1024) NOT NULL DEFAULT '',
    start_user VARCHAR(64) NOT NULL DEFAULT '',
    start_user_ke_id VARCHAR(20) NOT NULL DEFAULT '0',
    status TINYINT NOT NULL DEFAULT 0,
    end_reason VARCHAR(64) NOT NULL DEFAULT '',
    proc_type VARCHAR(31) NOT NULL DEFAULT '',
    biz_scene VARCHAR(31) NOT NULL DEFAULT '',
    proc_desc VARCHAR(1024) NOT NULL DEFAULT '',
    city_code INT NOT NULL DEFAULT 0,
    source VARCHAR(128) NOT NULL DEFAULT '',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_inst_proc_def_id (proc_def_id),
    KEY idx_process_inst_relate_proc_inst_id (relate_proc_inst_id),
    KEY idx_process_inst_relate_task_inst_id (relate_task_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    `key` VARCHAR(64) NOT NULL DEFAULT '',
    key_cn VARCHAR(64) NOT NULL DEFAULT '',
    `value` VARCHAR(2000) NOT NULL DEFAULT '',
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_inst_data_proc_inst_id (proc_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst_data_init_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_def_id BIGINT NOT NULL DEFAULT 0,
    city_code VARCHAR(16) NOT NULL DEFAULT '',
    `key` VARCHAR(64) NOT NULL DEFAULT '',
    `value` VARCHAR(1024) NOT NULL DEFAULT '',
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_inst_data_init_config_proc (proc_def_id, city_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst_data_packet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    data_packet_value_id BIGINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_packet_proc_inst_id (proc_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst_data_packet_value (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    proc_task_inst_id BIGINT NOT NULL DEFAULT 0,
    task_code VARCHAR(64) NOT NULL DEFAULT '',
    init_value TEXT,
    `value` TEXT,
    init_source TEXT,
    source TEXT,
    status TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_packet_value_proc_task (proc_inst_id, proc_task_inst_id),
    KEY idx_packet_value_proc_task_code (proc_inst_id, task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_inst_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    process_inst_id BIGINT NOT NULL DEFAULT 0,
    relation_code VARCHAR(64) NOT NULL DEFAULT '',
    relation_type VARCHAR(64) NOT NULL DEFAULT '',
    relation_inst_id VARCHAR(64) NOT NULL DEFAULT '',
    process_def_id BIGINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_inst_relation_process_inst_id (process_inst_id),
    KEY idx_process_inst_relation_relation (relation_type, relation_code, relation_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    task_inst_id BIGINT NOT NULL DEFAULT 0,
    stage VARCHAR(32) NOT NULL DEFAULT '',
    result VARCHAR(32) NOT NULL DEFAULT '',
    content VARCHAR(2000) NOT NULL DEFAULT '',
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_log_inst (proc_inst_id, task_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_task_inst (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    task_code VARCHAR(64) NOT NULL DEFAULT '',
    task_name VARCHAR(128) NOT NULL DEFAULT '',
    request_id VARCHAR(64) NOT NULL DEFAULT '',
    status TINYINT NOT NULL DEFAULT 0,
    operator_id BIGINT NOT NULL DEFAULT 0,
    operator_name VARCHAR(20) NOT NULL DEFAULT '',
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uniq_process_task_inst_request_id (request_id),
    KEY idx_process_task_inst_proc_code (proc_inst_id, task_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_task_inst_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_task_inst_id BIGINT NOT NULL DEFAULT 0,
    `key` VARCHAR(64) NOT NULL DEFAULT '',
    key_cn VARCHAR(64) NOT NULL DEFAULT '',
    `value` VARCHAR(2000) NOT NULL DEFAULT '',
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_process_task_inst_data_proc (proc_task_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_task_inst_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_inst_id BIGINT NOT NULL DEFAULT 0,
    relation_type VARCHAR(64) NOT NULL DEFAULT '',
    relation_inst_id VARCHAR(128) NOT NULL DEFAULT '',
    status TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_relation_task_type_inst (task_inst_id, relation_type, relation_inst_id),
    KEY idx_task_relation_task_inst_id (task_inst_id),
    KEY idx_task_relation_external (relation_type, relation_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS process_task_inst_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    proc_task_inst_id BIGINT NOT NULL DEFAULT 0,
    ucid VARCHAR(64) NOT NULL DEFAULT '',
    code VARCHAR(64) NOT NULL DEFAULT '',
    name VARCHAR(64) NOT NULL DEFAULT '',
    is_delete TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_candidate_proc_inst_id (proc_inst_id),
    KEY idx_task_candidate_task_inst_id (proc_task_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS candidate_inst_relation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    proc_inst_id BIGINT NOT NULL DEFAULT 0,
    relate_inst_id VARCHAR(64) NOT NULL DEFAULT '0',
    ucid VARCHAR(64) NOT NULL DEFAULT '',
    code VARCHAR(64) NOT NULL DEFAULT '',
    name VARCHAR(64) NOT NULL DEFAULT '',
    deleted TINYINT NOT NULL DEFAULT 0,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `type` INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    task_name VARCHAR(31) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    KEY idx_candidate_inst_relation_proc_inst_id (proc_inst_id),
    KEY idx_candidate_inst_relation_relate_inst_id (relate_inst_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_record (
    event_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL DEFAULT '',
    payload_class VARCHAR(512) NOT NULL DEFAULT '',
    payload TEXT NOT NULL,
    occurred_at DATETIME NOT NULL,
    subject_type VARCHAR(64) NOT NULL DEFAULT '',
    subject_id VARCHAR(128) NOT NULL DEFAULT '',
    partition_key VARCHAR(128) NOT NULL DEFAULT '',
    correlation_id VARCHAR(128) NOT NULL DEFAULT '',
    causation_event_id BIGINT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT '',
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_at DATETIME DEFAULT NULL,
    PRIMARY KEY (event_id),
    KEY idx_event_record_status (status, event_id),
    KEY idx_event_record_partition (partition_key, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_delivery (
    event_id BIGINT NOT NULL,
    listener_name VARCHAR(128) NOT NULL DEFAULT '',
    status VARCHAR(32) NOT NULL DEFAULT '',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME DEFAULT NULL,
    last_error TEXT,
    ctime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mtime DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, listener_name),
    KEY idx_event_delivery_due (status, next_attempt_at, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
