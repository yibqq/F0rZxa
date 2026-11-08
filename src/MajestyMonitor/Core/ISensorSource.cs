using System;
using System.Collections.Generic;

namespace MajestyMonitor.Core
{
    public interface ISensorSource : IDisposable
    {
        /// <summary>Opens hardware access. May be slow; called on a background thread.</summary>
        void Open();

        /// <summary>Refreshes every sensor and returns the values for the current bindings.</summary>
        Snapshot Read(IDictionary<MetricKind, string> bindings);

        /// <summary>Sensors that can feed <paramref name="kind"/>, best match first.</summary>
        IList<SensorOption> GetOptions(MetricKind kind);

        /// <summary>A human readable warning (missing driver, no admin rights...) or null.</summary>
        string Warning { get; }
    }
}
