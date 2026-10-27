package com.junaldadlawan.event_ticketing_api.venue.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import com.junaldadlawan.event_ticketing_api.event.cache.PublicEventCacheEvictionListener;
import com.junaldadlawan.event_ticketing_api.venue.enums.VenueType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@EntityListeners(PublicEventCacheEvictionListener.class)
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venue extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", length = 30)
    private VenueType venueType;

    /** ISO 3166-1 alpha-2, upper-case. */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "description", length = 2000)
    private String description;

    /** People the venue holds; may be more than the seats of its seat layout. */
    @Column(name = "max_capacity")
    private Integer maxCapacity;

    /** The URL of an image uploaded through the upload endpoint. */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;
}
