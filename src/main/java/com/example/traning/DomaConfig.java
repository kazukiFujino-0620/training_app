//package com.example.traning;
//
//import javax.sql.DataSource;
//
//import org.seasar.doma.jdbc.Config;
//import org.seasar.doma.jdbc.dialect.Dialect;
//import org.springframework.stereotype.Component;
//
//@Component
//public class DomaConfig implements Config {
//
//	private final DataSource dataSource;
//	private final Dialect dialect;
//
//	public DomaConfig(DataSource dataSource, Dialect dialect) {
//		this.dataSource = dataSource;
//		this.dialect = dialect;
//	}
//
//	@Override
//	public DataSource getDataSource() {
//		return dataSource;
//	}
//
//	@Override
//	public Dialect getDialect() {
//		return dialect;
//	}
//}
