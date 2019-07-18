/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables.daos;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.5.3"
	},
	comments = "This class is generated by jOOQ"
)
@java.lang.SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ServiceDao extends org.jooq.impl.DAOImpl<org.constellation.database.api.jooq.tables.records.ServiceRecord, org.constellation.database.api.jooq.tables.pojos.Service, java.lang.Integer> {

	/**
	 * Create a new ServiceDao without any configuration
	 */
	public ServiceDao() {
		super(org.constellation.database.api.jooq.tables.Service.SERVICE, org.constellation.database.api.jooq.tables.pojos.Service.class);
	}

	/**
	 * Create a new ServiceDao with an attached configuration
	 */
	public ServiceDao(org.jooq.Configuration configuration) {
		super(org.constellation.database.api.jooq.tables.Service.SERVICE, org.constellation.database.api.jooq.tables.pojos.Service.class, configuration);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected java.lang.Integer getId(org.constellation.database.api.jooq.tables.pojos.Service object) {
		return object.getId();
	}

	/**
	 * Fetch records that have <code>id IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchById(java.lang.Integer... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.ID, values);
	}

	/**
	 * Fetch a unique record that has <code>id = value</code>
	 */
	public org.constellation.database.api.jooq.tables.pojos.Service fetchOneById(java.lang.Integer value) {
		return fetchOne(org.constellation.database.api.jooq.tables.Service.SERVICE.ID, value);
	}

	/**
	 * Fetch records that have <code>identifier IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByIdentifier(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.IDENTIFIER, values);
	}

	/**
	 * Fetch records that have <code>type IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByType(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.TYPE, values);
	}

	/**
	 * Fetch records that have <code>date IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByDate(java.lang.Long... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.DATE, values);
	}

	/**
	 * Fetch records that have <code>config IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByConfig(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.CONFIG, values);
	}

	/**
	 * Fetch records that have <code>owner IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByOwner(java.lang.Integer... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.OWNER, values);
	}

	/**
	 * Fetch records that have <code>status IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByStatus(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.STATUS, values);
	}

	/**
	 * Fetch records that have <code>versions IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByVersions(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.VERSIONS, values);
	}

	/**
	 * Fetch records that have <code>impl IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.Service> fetchByImpl(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.Service.SERVICE.IMPL, values);
	}
}
