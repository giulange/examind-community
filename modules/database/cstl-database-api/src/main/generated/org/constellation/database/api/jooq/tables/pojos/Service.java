/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables.pojos;

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
public class Service implements java.io.Serializable {

	private static final long serialVersionUID = 1996334932;

	private java.lang.Integer id;
	private java.lang.String  identifier;
	private java.lang.String  type;
	private java.lang.Long    date;
	private java.lang.String  config;
	private java.lang.Integer owner;
	private java.lang.String  status;
	private java.lang.String  versions;
	private java.lang.String  impl;

	public Service() {}

	public Service(
		java.lang.Integer id,
		java.lang.String  identifier,
		java.lang.String  type,
		java.lang.Long    date,
		java.lang.String  config,
		java.lang.Integer owner,
		java.lang.String  status,
		java.lang.String  versions,
		java.lang.String  impl
	) {
		this.id = id;
		this.identifier = identifier;
		this.type = type;
		this.date = date;
		this.config = config;
		this.owner = owner;
		this.status = status;
		this.versions = versions;
		this.impl = impl;
	}

	@javax.validation.constraints.NotNull
	public java.lang.Integer getId() {
		return this.id;
	}

	public Service setId(java.lang.Integer id) {
		this.id = id;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 512)
	public java.lang.String getIdentifier() {
		return this.identifier;
	}

	public Service setIdentifier(java.lang.String identifier) {
		this.identifier = identifier;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 32)
	public java.lang.String getType() {
		return this.type;
	}

	public Service setType(java.lang.String type) {
		this.type = type;
		return this;
	}

	@javax.validation.constraints.NotNull
	public java.lang.Long getDate() {
		return this.date;
	}

	public Service setDate(java.lang.Long date) {
		this.date = date;
		return this;
	}

	public java.lang.String getConfig() {
		return this.config;
	}

	public Service setConfig(java.lang.String config) {
		this.config = config;
		return this;
	}

	public java.lang.Integer getOwner() {
		return this.owner;
	}

	public Service setOwner(java.lang.Integer owner) {
		this.owner = owner;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 32)
	public java.lang.String getStatus() {
		return this.status;
	}

	public Service setStatus(java.lang.String status) {
		this.status = status;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 32)
	public java.lang.String getVersions() {
		return this.versions;
	}

	public Service setVersions(java.lang.String versions) {
		this.versions = versions;
		return this;
	}

	@javax.validation.constraints.Size(max = 255)
	public java.lang.String getImpl() {
		return this.impl;
	}

	public Service setImpl(java.lang.String impl) {
		this.impl = impl;
		return this;
	}
}
